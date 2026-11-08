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

import io.github.photowey.kttp.idea.cli.KttpCliInvocation
import io.github.photowey.kttp.idea.cli.KttpCliJsonResultParser
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

/** Recovers only this process's checkpoint; never guesses from shared response directories. */
internal fun recoverScriptResponse(
    checkpoint: File?,
    invocation: KttpCliInvocation,
    failure: KttpExecutionResult
): KttpExecutionResult {
    if (failure.success || failure.statusCode != null || checkpoint?.isFile != true) return failure
    return runCatching {
        val captured = KttpCliJsonResultParser.parse(failure.scriptName, checkpoint.readText())
        if (captured.statusCode == null || captured.scriptName != File(invocation.scriptPath).name) return failure
        val directory =
            File(invocation.responseDir ?: File(System.getProperty("java.io.tmpdir"), ".kttp-responses").path)
                .canonicalFile.toPath()
        val responseFile = captured.responseFilePath?.let { path ->
            File(path).canonicalFile.takeIf {
                it.isFile && it.name != "kttp-history.json" && it.toPath().startsWith(directory)
            }
                ?: return failure
        } ?: saveConfirmedEmptyResponse(captured, directory)
        captured.copy(
            success = false, message = failure.message, assertionsPassed = false,
            responseFilePath = responseFile?.path
        )
    }.getOrDefault(failure)
}

/**
 * Creates a headers-only fallback when a checkpoint confirms a zero-byte response and has no saved response file.
 * Incomplete or nonempty previews cannot be reconstructed here; persistence failure returns null.
 */
private fun saveConfirmedEmptyResponse(response: KttpExecutionResult, directory: Path): File? {
    if (response.responseSize != 0L || response.bodyTruncated || !response.body.isNullOrEmpty()) return null
    return runCatching {
        Files.createDirectories(directory)
        val file = Files.createTempFile(directory, "kttp-empty-response-", ".txt")
        try {
            val content = buildString {
                append(response.protocol ?: "HTTP/1.1").append(' ').append(response.statusCode).append("\r\n")
                response.headers.forEach { (name, value) -> append(name).append(": ").append(value).append("\r\n") }
                append("\r\n")
            }
            Files.writeString(file, content, WRITE, TRUNCATE_EXISTING, NOFOLLOW_LINKS)
            file.toFile()
        } catch (failure: Exception) {
            Files.deleteIfExists(file)
            throw failure
        }
    }.getOrNull()
}
