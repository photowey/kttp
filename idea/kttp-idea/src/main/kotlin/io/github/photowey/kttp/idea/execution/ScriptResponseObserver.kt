/*
 * Copyright (c) 2026-present The Kttp Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.photowey.kttp.idea.execution

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import io.github.photowey.kttp.idea.cli.KttpCliJsonResultParser
import java.io.File
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Watches one private CLI checkpoint for provisional responses while its script is still running.
 * File reads and decoding stay off EDT; closing releases the watcher even during cancellation.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/07
 */
internal class ScriptResponseObserver(
    private val checkpoint: File,
    private val title: String,
    private val onPreview: (KttpExecutionResult) -> Unit
) : AutoCloseable {
    private val closed = AtomicBoolean()
    private val watcher = FileSystems.getDefault().newWatchService()
    private var lastContent: String? = null
    private val task: java.util.concurrent.Future<*>

    init {
        try {
            checkpoint.parentFile.toPath().register(watcher, ENTRY_CREATE, ENTRY_MODIFY)
            task = ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    while (!closed.get()) {
                        val key = watcher.take()
                        val changed = key.pollEvents().any {
                            it.kind() == OVERFLOW || it.context().toString() == checkpoint.name
                        }
                        if (changed) publish()
                        if (!key.reset()) break
                    }
                } catch (_: ClosedWatchServiceException) {
                    // Closing the execution releases a thread blocked in take().
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        } catch (failure: Exception) {
            watcher.close()
            throw failure
        }
    }

    private fun publish() {
        if (closed.get() || !checkpoint.isFile) return
        try {
            val content = checkpoint.readText()
            if (content == lastContent) return
            val response = KttpCliJsonResultParser.parse(title, content)
            if (response.statusCode == null || response.scriptName != title) return
            lastContent = content
            if (!closed.get()) onPreview(response)
        } catch (failure: Exception) {
            if (!closed.get()) Logger.getInstance(ScriptResponseObserver::class.java).debug("Cannot read response preview", failure)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        watcher.close()
        task.cancel(true)
    }
}
