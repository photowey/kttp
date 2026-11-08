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

import io.github.photowey.kttp.cli.model.CliExecutionResult
import io.github.photowey.kttp.cli.util.ResponseBodyParser
import io.github.photowey.kttp.core.interceptor.CapturedRequest
import io.github.photowey.kttp.core.interceptor.MAX_RESPONSE_PREVIEW_BYTES
import okhttp3.Protocol
import okhttp3.Response
import java.io.File

/** Builds the same bounded response representation before and after script callbacks. */
internal fun capturedResponseResult(
    scriptName: String,
    response: Response,
    capturedRequest: CapturedRequest?,
    responseFile: File?,
    executionTimeMs: Long? = null
): CliExecutionResult {
    val bodyString = response.peekBody(MAX_RESPONSE_PREVIEW_BYTES).string()
    val parsedBody = ResponseBodyParser.parse(response, bodyString)
    val request = response.request
    val charset = response.body?.contentType()?.charset() ?: Charsets.UTF_8
    val responseSize = responseFile?.takeIf(File::isFile)?.length()
        ?: bodyString.toByteArray(charset).size.toLong()
    val protocol = when (response.protocol) {
        Protocol.HTTP_1_0 -> "HTTP/1.0"
        Protocol.HTTP_1_1 -> "HTTP/1.1"
        Protocol.HTTP_2, Protocol.H2_PRIOR_KNOWLEDGE -> "HTTP/2"
        else -> response.protocol.name
    }
    return CliExecutionResult(
        success = true,
        scriptName = scriptName,
        message = "OK",
        statusCode = response.code,
        statusMessage = response.message.ifEmpty { "OK" },
        protocol = protocol,
        headers = response.headers.toMultimap().mapValues { it.value.joinToString(", ") },
        body = parsedBody.content ?: bodyString,
        contentType = response.headers["Content-Type"],
        bodyType = parsedBody.type.name,
        responseFilePath = responseFile?.absolutePath,
        requestMethod = capturedRequest?.method ?: request.method,
        requestUrl = capturedRequest?.url ?: request.url.toString(),
        requestHeaders = capturedRequest?.headers ?: request.headers.toMultimap()
            .mapValues { it.value.joinToString(", ") },
        requestBody = capturedRequest?.bodyString,
        bodyTruncated = responseSize > MAX_RESPONSE_PREVIEW_BYTES,
        responseSize = responseSize,
        executionTimeMs = executionTimeMs,
        httpTimeMs = (response.receivedResponseAtMillis - response.sentRequestAtMillis).coerceAtLeast(0)
    )
}
