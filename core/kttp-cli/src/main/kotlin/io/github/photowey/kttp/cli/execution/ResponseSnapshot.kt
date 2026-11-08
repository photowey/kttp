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

package io.github.photowey.kttp.cli.execution

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import io.github.photowey.kttp.core.interceptor.CapturedRequest
import io.github.photowey.kttp.cli.model.CliExecutionResult
import okhttp3.Response

/**
 * Publishes per-process response metadata and a bounded body preview before user `then` callbacks.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/06
 */
internal class ResponseSnapshot(private val file: File, private val scriptName: String) {
    fun clear() {
        file.parentFile?.mkdirs()
        file.delete()
    }

    /**
     * Publishes the current bounded response snapshot through a sibling temporary file.
     * Atomic replacement is preferred; filesystems without atomic moves use ordinary replacement.
     */
    fun publish() {
        val response = LastResponseHolder.response.get() ?: return
        val result = capturedResponseResult(
            scriptName, response, LastResponseHolder.request.get(),
            LastResponseHolder.savedResponseFilePath.get()?.let(::File)
        )
        publish(result)
    }

    /** Publishes an explicitly provisional preview before the full response file is written. */
    fun publishPreview(response: Response, request: CapturedRequest?) {
        publish(capturedResponseResult(scriptName, response, request, null).copy(
            responseSize = null,
            bodyTruncated = response.body?.contentLength() == io.github.photowey.kttp.core.interceptor.MAX_RESPONSE_PREVIEW_BYTES
        ))
    }

    private fun publish(result: CliExecutionResult) {
        val temporary = Files.createTempFile(file.absoluteFile.parentFile.toPath(), ".kttp-snapshot-", ".tmp")
        try {
            Files.writeString(temporary, result.toJson())
            try {
                Files.move(temporary, file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file.toPath(), REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
