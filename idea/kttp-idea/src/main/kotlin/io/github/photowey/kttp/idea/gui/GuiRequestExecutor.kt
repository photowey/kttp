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

package io.github.photowey.kttp.idea.gui

import io.github.photowey.kttp.core.context.KttpContext
import io.github.photowey.kttp.core.context.ClientContextImpl
import io.github.photowey.kttp.core.context.NoopCookiesContext
import io.github.photowey.kttp.core.context.createKttpContext
import io.github.photowey.kttp.core.context.storage.InMemoryStorage
import io.github.photowey.kttp.core.context.store.StrictEnvironmentStore
import io.github.photowey.kttp.core.interceptor.CapturedRequest
import io.github.photowey.kttp.core.interceptor.KttpInterceptor
import io.github.photowey.kttp.core.interceptor.MAX_RESPONSE_PREVIEW_BYTES
import io.github.photowey.kttp.core.printer.NoopPrinter
import io.github.photowey.kttp.dsl.builder.RequestBuilder
import io.github.photowey.kttp.idea.execution.KttpExecutionResult
import okhttp3.Request
import okhttp3.Response
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Runs Quick Request synchronously from a projected RequestSpec without evaluating Kotlin.
 * Owns its HTTP resources and returns a bounded response preview plus the persisted response association.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
object GuiRequestExecutor {

    private const val REQUEST_TIMEOUT_SECONDS = 60L

    /**
     * Synchronously executes a projected request without evaluating Kotlin; call from a worker thread.
     * Uses strict environment values, an in-memory store, no persistent cookie jar, and a fixed call timeout.
     * Returns captured response details or an execution failure and always closes owned resources.
     */
    fun execute(
        draft: GuiDraft,
        environment: Map<String, String>,
        baseDir: String?,
        responseDir: Path,
        cancellation: GuiRequestCancellation? = null,
        onResponsePreview: ((KttpExecutionResult) -> Unit)? = null
    ): KttpExecutionResult {
        val capture = ResponseCapture()
        val context = createContext(responseDir, capture, environment, draft.title, onResponsePreview)
        return try {
            val builder = createRequestBuilder(draft.request, environment, baseDir, context)
            val request = builder.build()
            val client = context.clientContext.getClient(builder.getClientConfigurer()).newBuilder()
                .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            val call = client.newCall(request)
            cancellation?.attach(call)
            try {
                call.execute().use { response -> buildResult(draft.title, request, response, capture) }
            } finally {
                cancellation?.detach(call)
            }
        } catch (e: Exception) {
            val message = if (cancellation?.isCancelled == true) "Request cancelled" else e.message ?: e.toString()
            capture.provisional?.copy(success = false, message = message, assertionsPassed = false)
                ?: KttpExecutionResult(scriptName = draft.title, success = false, message = message)
        } finally {
            capture.response?.close()
            context.close()
        }
    }

    private fun createContext(
        responseDir: Path, capture: ResponseCapture, environment: Map<String, String>, title: String,
        onResponsePreview: ((KttpExecutionResult) -> Unit)?
    ): KttpContext = createKttpContext(
        storage = InMemoryStorage(),
        environmentStore = StrictEnvironmentStore(environment),
        cookiesContext = NoopCookiesContext,
        clientContext = ClientContextImpl(
            KttpInterceptor(
                printer = NoopPrinter,
                responseStorageDir = responseDir,
                onRequestCaptured = { capture.request = it },
                onResponseCaptured = { capture.response = it },
                onResponseFileSaved = { capture.savedPath = it },
                onResponsePreview = { preview ->
                    val provisional = buildResult(title, preview.request, preview, ResponseCapture().apply {
                        request = capture.request
                        response = preview
                    }).copy(bodyTruncated = preview.body?.contentLength() == MAX_RESPONSE_PREVIEW_BYTES)
                    capture.provisional = provisional
                    onResponsePreview?.invoke(provisional)
                }
            )
        ),
        printer = NoopPrinter
    )

    private fun createRequestBuilder(
        spec: RequestSpec,
        environment: Map<String, String>,
        baseDir: String?,
        context: KttpContext
    ): RequestBuilder {
        val url = resolveValue(spec.target.url, environment, baseDir)
        return RequestBuilder(spec.method.value, url, context).apply {
            spec.target.pathParams.forEach { pathParam ->
                path(pathParam.name, resolveValue(pathParam.value, environment, baseDir))
            }
            spec.target.queryParams.forEach { param ->
                query(param.name, resolveValue(param.value, environment, baseDir))
            }
            spec.headers.forEach { header ->
                header(header.name, resolveValue(header.value, environment, baseDir))
            }
            applyAuth(this, spec.auth, environment, baseDir)
            applyBody(this, spec.body, environment, baseDir, spec.headers.any { it.name.equals("Content-Type", true) })
            applyTransport(this, spec.transport)
        }
    }

    private fun buildResult(
        title: String,
        request: Request,
        response: Response,
        capture: ResponseCapture
    ): KttpExecutionResult {
        val preview = capture.response ?: response.newBuilder()
            .body(response.peekBody(MAX_RESPONSE_PREVIEW_BYTES.toLong())).build()
        val responseBody = preview.use { it.body?.string() }
        val savedSize = capture.savedPath?.let { Files.size(Paths.get(it)) }
        return KttpExecutionResult(
            scriptName = title,
            success = true,
            message = "OK",
            responseFilePath = capture.savedPath,
            statusCode = response.code,
            httpTimeMs = (response.receivedResponseAtMillis - response.sentRequestAtMillis).coerceAtLeast(0),
            protocol = when (response.protocol) {
                okhttp3.Protocol.HTTP_1_0 -> "HTTP/1.0"
                okhttp3.Protocol.HTTP_1_1 -> "HTTP/1.1"
                okhttp3.Protocol.HTTP_2, okhttp3.Protocol.H2_PRIOR_KNOWLEDGE -> "HTTP/2"
                else -> response.protocol.name
            },
            headers = response.headers.toMultimap().mapValues { it.value.joinToString(", ") },
            body = responseBody,
            contentType = response.header("Content-Type"),
            bodyTruncated = savedSize != null && savedSize > MAX_RESPONSE_PREVIEW_BYTES,
            responseSize = savedSize,
            requestMethod = capture.request?.method ?: request.method,
            requestUrl = capture.request?.url ?: request.url.toString(),
            requestHeaders = capture.request?.headers ?: request.headers.toMultimap()
                .mapValues { it.value.joinToString(", ") },
            requestBody = capture.request?.bodyString
        )
    }

    /**
     * Collects the request, response, and saved-file association for one GUI execution.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    private class ResponseCapture {
        var request: CapturedRequest? = null
        var response: Response? = null
        var savedPath: String? = null
        var provisional: KttpExecutionResult? = null
    }

    private fun applyAuth(
        builder: RequestBuilder,
        auth: AuthSpec,
        environment: Map<String, String>,
        baseDir: String?
    ) {
        when (auth) {
            is AuthSpec.Inherit -> Unit
            is AuthSpec.Bearer -> {
                builder.header("Authorization", "Bearer ${resolveValue(auth.token, environment, baseDir)}")
            }

            is AuthSpec.Basic -> {
                val username = resolveValue(auth.username, environment, baseDir)
                val password = resolveValue(auth.password, environment, baseDir)
                val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
                builder.header("Authorization", "Basic $token")
            }

            is AuthSpec.ApiKey -> {
                val value = resolveValue(auth.keyValue, environment, baseDir)
                when (auth.placement) {
                    ApiKeyPlacement.HEADER -> builder.header(auth.keyName, value)
                    ApiKeyPlacement.QUERY -> builder.query(auth.keyName, value)
                }
            }
        }
    }

    private fun applyBody(
        builder: RequestBuilder,
        body: BodySpec,
        environment: Map<String, String>,
        baseDir: String?,
        hasContentType: Boolean
    ) {
        when (body) {
            is BodySpec.None -> Unit
            is BodySpec.Json -> {
                if (!hasContentType) builder.header("Content-Type", "application/json")
                builder.body(resolveValue(body.content, environment, baseDir))
            }

            is BodySpec.Raw -> {
                body.contentType?.takeUnless { hasContentType }?.let {
                    builder.header("Content-Type", resolveValue(it, environment, baseDir))
                }
                builder.body(resolveValue(body.content, environment, baseDir))
            }

            is BodySpec.FormUrlEncoded -> {
                builder.formData {
                    body.fields.forEach { field ->
                        field(field.name, resolveValue(field.value, environment, baseDir))
                    }
                }
            }
        }
    }

    private fun applyTransport(builder: RequestBuilder, transport: TransportSpec) {
        if (transport.noRedirect) {
            builder.noRedirect()
        }
        if (transport.noCookies) {
            builder.noCookies()
        }
        if (transport.http2PriorKnowledge) {
            builder.http2()
        }
    }

    /**
     * Resolves a literal, selected environment value, or local text resource for Quick Request.
     * Missing required values fail; optional file failures yield empty text. Opaque Kotlin expressions are rejected.
     */
    private fun resolveValue(valueRef: ValueRef, environment: Map<String, String>, baseDir: String?): String {
        return when (valueRef) {
            is ValueRef.Literal -> valueRef.value
            is ValueRef.Env -> environment[valueRef.key]
                ?: throw IllegalStateException("Environment variable '${valueRef.key}' is not set")

            is ValueRef.LocalFile -> {
                val resource = io.github.photowey.kttp.core.resource.ResourceResolver(baseDir).resolve(valueRef.path)
                if (!resource.exists()) {
                    if (valueRef.required) throw IllegalStateException("Local file not found: ${resource.getAbsolutePath()}")
                    ""
                } else {
                    try {
                        resource.readText(Charset.forName(valueRef.charset))
                    } catch (e: Exception) {
                        if (valueRef.required) throw e
                        ""
                    }
                }
            }

            is ValueRef.OpaqueScript -> throw IllegalStateException("GUI Send does not support script-only expressions")
        }
    }

    fun resolveResponseDir(projectBasePath: String?): Path {
        return projectBasePath?.let {
            Paths.get(io.github.photowey.kttp.idea.path.KttpProjectPath.getResponsesDir(it).absolutePath)
        } ?: Paths.get(io.github.photowey.kttp.idea.path.KttpConsolesPath.getResponsesDir().absolutePath)
    }
}
