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

package io.github.photowey.kttp.idea.io

import io.github.photowey.kttp.idea.execution.RequestExecutionMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import io.github.photowey.kttp.idea.path.KttpConsolesPath
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Application-global history stored beneath Scratches and Consoles, shared by GUI and Run.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
object HistoryPersistence {
    private const val HISTORY_FILE = "kttp-history.json"
    private val repository = HistoryRepository { File(KttpConsolesPath.getResponsesDir(), HISTORY_FILE) }

    /**
     * Stores one execution's script and response paths together with mode and timing metadata.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    @Serializable
    data class Record(
        val scriptName: String,
        val responseFilePath: String,
        val scriptFilePath: String? = null,
        val executionMode: RequestExecutionMode? = null,
        val totalTimeMs: Long? = null,
        val httpTimeMs: Long? = null
    )

    /**
     * Represents the persisted collection of execution history records.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    @Serializable
    data class HistoryFile(val entries: List<Record> = emptyList())

    fun append(record: Record): List<Record> = repository.append(listOf(record))

    fun append(records: List<Record>): List<Record> = repository.append(records)

    fun clear(): List<Record> = repository.clear()

    fun load(): List<Record> = repository.load()

    /**
     * Subscribes until the parent is disposed and logs listener exceptions.
     * Delivery stays on the writer thread; UI listeners must arrange EDT access themselves.
     */
    fun subscribe(parent: Disposable, listener: (List<Record>) -> Unit) {
        val unsubscribe = repository.subscribe { records ->
            try {
                listener(records)
            } catch (failure: Exception) {
                Logger.getInstance(HistoryPersistence::class.java).warn("Cannot refresh history view", failure)
            }
        }
        Disposer.register(parent, Disposable { unsubscribe() })
    }
}
