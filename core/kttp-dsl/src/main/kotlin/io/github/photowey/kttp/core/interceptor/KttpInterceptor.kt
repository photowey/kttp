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

package io.github.photowey.kttp.core.interceptor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.photowey.kttp.core.printer.Printer
import io.github.photowey.kttp.core.printer.Printer.Color
import io.github.photowey.kttp.core.printer.println
import io.github.photowey.kttp.core.extension.http.ContentDisposition
import io.github.photowey.kttp.core.extension.http.filename
import io.github.photowey.kttp.core.extension.okhttp.fileExtension
import io.github.photowey.kttp.core.extension.okhttp.hasSubTypeOf
import io.github.photowey.kttp.core.extension.okhttp.contentDisposition
import io.github.photowey.kttp.core.curl.CurlConverter
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.net.URLDecoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val MAX_RESPONSE_LOG_BYTES = 2000L

/**
 * Captured request data for logging or IDE (e.g. Run Console).
 * Optional callback [KttpInterceptor] constructor can receive this.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
data class CapturedRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val bodyString: String?
)

/**
 * Logs and optionally persists responses without retaining complete downloads in memory.
 * [onResponseCaptured] receives a decoded preview of at most [MAX_RESPONSE_PREVIEW_BYTES].
 * Optional [onResponsePreview] receives a provisional preview before persistence; its response is callback-scoped.
 * The returned response retains the complete body and must be closed by its consumer.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpInterceptor(
    private val printer: Printer,
    private val responseStorageDir: Path?,
    private val onRequestCaptured: ((CapturedRequest) -> Unit)? = null,
    private val onResponseCaptured: ((Response) -> Unit)? = null,
    private val onResponseFileSaved: ((String) -> Unit)? = null,
    private val curlMode: Boolean = false,
    private val curlOutput: ((String) -> Unit)? = null,
    private val onResponsePreview: ((Response) -> Unit)? = null
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response = interceptResponse(chain, observeRequest = true)

    internal val isCurlMode: Boolean get() = curlMode && curlOutput != null

    /** CURL runs after user application transformations; HTTP capture runs around the final response. */
    internal fun interceptFinalResponse(chain: Interceptor.Chain): Response =
        if (isCurlMode) chain.proceed(chain.request()) else interceptResponse(chain, observeRequest = false)

    internal fun observeOutgoingRequest(request: Request): Request {
        val (prepared, bodyString) = peekRequestBody(request)
        onRequestCaptured?.invoke(
            CapturedRequest(
                method = prepared.method,
                url = prepared.url.toString(),
                headers = prepared.headers.names().associateWith { prepared.header(it).orEmpty() },
                bodyString = bodyString
            )
        )
        if (!curlMode) logRequest(prepared, bodyString)
        return prepared
    }

    private fun interceptResponse(chain: Interceptor.Chain, observeRequest: Boolean): Response {
        val requestToSend = if (observeRequest || curlMode) observeOutgoingRequest(chain.request()) else chain.request()
        if (curlMode && curlOutput != null) {
            val t0 = System.currentTimeMillis()
            val curlCommand = CurlConverter.toCurl(requestToSend)
            System.err.println("[KTTP_TIMING] curl_convert_ms=${System.currentTimeMillis() - t0}")
            curlOutput(curlCommand)
            return newEmptyResponse(requestToSend)
        }

        val response = try {
            chain.proceed(requestToSend)
        } catch (e: Exception) {
            printer.println("Connection refused", Color.RED)
            throw e
        }

        val captured = try {
            onResponsePreview?.let { callback -> captureResponsePreview(response).use(callback) }
            captureResponse(
                response, responseStorageDir,
                calculateResponseFilename(
                    response.request.url,
                    response.contentDisposition(),
                    response.body?.contentType()
                ),
                chain.call()::isCanceled
            )
        } catch (error: Throwable) {
            response.close()
            throw error
        }
        try {
            logResponse(captured.preview)
            onResponseCaptured?.invoke(captured.preview)
            captured.savedPath?.let(::reportSavedResponse)
            return captured.response
        } catch (error: Throwable) {
            captured.response.close()
            throw error
        }
    }

    private fun newEmptyResponse(request: Request): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("".toResponseBody(null))
            .build()
    }

    /**
     * Writes the request body into memory and replaces it with a replayable byte-array body.
     * This avoids writing the original body twice; not every RequestBody supports repeated writes.
     * Returns the prepared request and its decoded body text, or null text when there is no body.
     */
    private fun peekRequestBody(request: Request): Pair<Request, String?> {
        val body = request.body ?: return request to null
        val buffer = Buffer()
        body.writeTo(buffer)
        val contentType = body.contentType()
        val charset = contentType?.charset() ?: StandardCharsets.UTF_8
        val bodyBytes = buffer.readByteArray()
        val bodyString = String(bodyBytes, charset)
        val newRequest = request.newBuilder()
            .method(request.method, bodyBytes.toRequestBody(contentType))
            .build()
        return newRequest to bodyString
    }

    private fun logRequest(request: Request, bodyString: String? = null) {
        logRequestUrl(request)
        logHeaders(request.headers)
        printer.println()
        if (bodyString != null) {
            // Pretty-print JSON request bodies.
            val formattedBody = formatRequestBodyIfJson(bodyString, request.body?.contentType())
            printer.print(formattedBody, Color.GREEN)
            printer.println()
            printer.println()
        }
    }

    /**
     * Pretty-prints valid JSON request bodies; other content is returned unchanged.
     */
    private fun formatRequestBodyIfJson(bodyString: String, contentType: MediaType?): String {
        if (contentType?.hasSubTypeOf("json") == true) {
            return try {
                val objectMapper = jacksonObjectMapper()
                val node = objectMapper.readTree(bodyString)
                objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(node)
            } catch (_: Exception) {
                bodyString
            }
        }
        return bodyString
    }

    private fun logRequestUrl(request: Request) {
        sequenceOf(request.method, request.url)
            .joinToString(separator = " ")
            .let { printer.println(it, Color.BLUE) }
    }

    private fun logResponse(response: Response) {
        logResponseStatus(response)
        logHeaders(response.headers)
        handleResponseBody(response)
    }

    private fun logResponseStatus(response: Response) {
        val protocol = when (response.protocol) {
            Protocol.HTTP_1_0 -> "HTTP/1.0"
            Protocol.HTTP_1_1 -> "HTTP/1.1"
            Protocol.HTTP_2, Protocol.H2_PRIOR_KNOWLEDGE -> "HTTP/2"
            else -> response.protocol.name
        }
        // Print protocol and status code without the status message.
        val text = "$protocol ${response.code}"
        printer.println(text, Color.BLUE)
    }

    private fun handleResponseBody(response: Response) {
        printer.println("")
        val responseBody = response.body ?: return
        val length = responseBody.contentLength()
        if (length in 1 until MAX_RESPONSE_LOG_BYTES && response.contentDisposition()?.filename() == null) {
            val buffer = Buffer().write(response.peekBody(MAX_RESPONSE_LOG_BYTES).bytes())
            logResponse(responseBody.contentType(), buffer)
        }
    }

    private fun logResponse(contentType: MediaType?, responseBuffer: Buffer) {
        val charset: Charset = contentType?.charset() ?: StandardCharsets.UTF_8
        val responseText = responseBuffer.readString(charset)

        val logText = when {
            contentType?.hasSubTypeOf("json") == true -> try {
                val objectMapper = jacksonObjectMapper()
                val node = objectMapper.readTree(responseText)
                objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(node)
            } catch (_: Exception) {
                responseText
            }

            else -> responseText
        }
        printer.println(logText, Color.GREEN)
    }

    private fun reportSavedResponse(filePath: Path) {
        val filePathString = filePath.toFile().absolutePath
        onResponseFileSaved?.invoke(filePathString)

        printer.println("")
        printer.println("Response file saved.", Color.GREEN)
        printer.println("> $filePathString", Color.GREEN)
        printer.println("")
    }

    private fun calculateResponseFilename(
        requestUrl: HttpUrl,
        contentDisposition: ContentDisposition?,
        contentType: MediaType?
    ): String {
        contentDisposition?.filename()
            ?.let { return it }

        requestUrl.filename()
            ?.let { return it }

        val fileExtension = contentType?.fileExtension() ?: "txt"
        return getCurrentTimestamp() + "." + fileExtension
    }

    private fun HttpUrl.filename(): String? = this
        .pathSegments
        .lastOrNull()
        ?.takeIf { segment -> '.' in segment }
        ?.let {
            URLDecoder.decode(it, StandardCharsets.UTF_8.name())
        }

    private fun getCurrentTimestamp(): String {
        val current = LocalDateTime.now()
        // Use yyyy-MM-dd'T'HHmmss for response file timestamps.
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss")
        return current.format(formatter)
    }

    private fun logHeaders(headers: Headers) {
        headers.joinToString(separator = " \n") { it.first + ": " + it.second }
            .let { printer.println(it, Color.BLUE) }
    }
}
