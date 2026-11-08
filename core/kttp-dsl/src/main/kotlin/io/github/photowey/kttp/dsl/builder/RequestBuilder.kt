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

package io.github.photowey.kttp.dsl.builder

import io.github.photowey.kttp.core.annotation.HeaderName
import io.github.photowey.kttp.core.annotation.HeaderValue
import io.github.photowey.kttp.core.annotation.KttpDSL
import io.github.photowey.kttp.core.annotation.RequestHeader
import io.github.photowey.kttp.core.util.UserAgentProvider
import io.github.photowey.kttp.core.constant.PATH_VARIABLE_REGEX
import io.github.photowey.kttp.core.context.ClientConfigurer
import io.github.photowey.kttp.core.context.KttpContext
import io.github.photowey.kttp.core.exception.MissingPathParameterException
import io.github.photowey.kttp.dsl.ByteArrayBody
import io.github.photowey.kttp.dsl.FormDataBody
import io.github.photowey.kttp.dsl.StringBody
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.http.HttpMethod

private fun buildHeaders(headers: List<Pair<String, Any>>) = Headers.Builder().apply {
    headers.forEach {
        add(it.first, it.second.toString())
    }
}.build()

private fun io.github.photowey.kttp.dsl.RequestBody.toOkHttpBody(contentType: MediaType? = null): RequestBody {
    return when (this) {
        is ByteArrayBody -> body.toRequestBody(contentType)
        is FormDataBody -> FormBody.Builder()
            .apply {
                for ((name, value) in body) {
                    add(name, value)
                }
            }
            .build()

        is StringBody -> body.toRequestBody(contentType)

        is io.github.photowey.kttp.dsl.MultipartBody -> MultipartBody.Builder(boundary)
            .apply {
                setType(MultipartBody.FORM)
                for (part in parts) {
                    check(part.body !is io.github.photowey.kttp.dsl.MultipartBody) { "Multipart body cannot be nested" }
                    addPart(
                        buildHeaders(part.headers),
                        part.body.toOkHttpBody(part.contentType)
                    )
                }
            }
            .build()
    }
}

/**
 * Configures the underlying OkHttp request builder through a receiver lambda.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
typealias RequestBuilderConfigurer = Request.Builder.() -> Unit

/**
 * Builds an HTTP request from DSL headers, query parameters, path values, body, and client options.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
@KttpDSL
class RequestBuilder(
    private val method: String,
    private val path: String,
    private val context: KttpContext?
) {
    private var noCookies = false
    private var noRedirect = false
    private var http2 = false

    private val requestBuilderTweaks: MutableList<RequestBuilderConfigurer> = mutableListOf()
    private val clientBuilderTweaks: MutableList<ClientConfigurer> = mutableListOf()

    private var body: io.github.photowey.kttp.dsl.RequestBody? = null
        set(value) {
            require(field == null) { "Body already set" }
            field = value
        }

    private var headers = mutableListOf<Pair<String, Any>>()
    private val queryParameters = mutableListOf<Pair<String, Any>>()
    private val pathMap = mutableMapOf<String, Any>()

    /** Appends a request-builder customization applied after the DSL URL, headers, method, and body. */
    fun configureRequest(configurer: RequestBuilderConfigurer) {
        requestBuilderTweaks += configurer
    }

    /** Appends a client customization applied after the request transport flags. */
    fun configure(configurer: ClientConfigurer) {
        clientBuilderTweaks += configurer
    }

    /** Omits the context cookie jar for this request; later client customizations can install another jar. */
    fun noCookies() {
        noCookies = true
    }

    /** Disables HTTP and HTTPS redirect following unless a later client customization overrides it. */
    fun noRedirect() {
        noRedirect = true
    }

    /** Uses H2_PRIOR_KNOWLEDGE as the sole protocol; this is not ordinary HTTP/2 negotiation. */
    fun http2() {
        http2 = true
    }

    // ----------------------------------------------------------------

    /** Appends header values and rejects empty names; existing headers with the same name are retained. */
    fun headers(@RequestHeader headers: Map<String, Any>) {
        headers.forEach { (key, value) ->
            require(key.isNotEmpty()) { "Header name must not be empty" }
        }
        this.headers.addAll(headers.map { it.key to it.value })
    }

    /** Appends headers in order, retaining repeated names; OkHttp validates them during build. */
    fun headers(@RequestHeader vararg headers: Pair<String, Any>) {
        this.headers.addAll(headers)
    }

    /** Appends one header without replacing earlier values with the same name. */
    fun header(
        @HeaderName key: String,
        @HeaderValue value: Any
    ) {
        headers.add(key to value)
    }

    // ----------------------------------------------------------------

    /** Sets a text body. Any second body assignment, including form or multipart, is rejected. */
    fun body(@io.github.photowey.kttp.core.annotation.RequestBody body: String) {
        this.body = StringBody(body)
    }

    /** Sets a byte-array body. Any second body assignment, including form or multipart, is rejected. */
    fun body(body: ByteArray) {
        this.body = ByteArrayBody(body)
    }

    // ----------------------------------------------------------------

    /** Sets a URL-encoded form body, retaining field order and repeated names. A body may be set only once. */
    fun formData(block: FormDataBodyBuilder.() -> Unit) {
        this.body = FormDataBodyBuilder().apply(block).build()
    }

    // ----------------------------------------------------------------

    /** Sets a multipart body with the supplied boundary. A body may be set only once. */
    fun multipart(boundary: String = "boundary", block: MultipartBodyBuilder.() -> Unit) {
        this.body = MultipartBodyBuilder(boundary).apply(block).build()
    }

    // ----------------------------------------------------------------

    /** Appends query values, rejecting empty names; OkHttp encodes names and values when building the URL. */
    fun queries(parameters: Map<String, Any>) {
        parameters.forEach { (key, value) ->
            require(key.isNotEmpty()) { "Parameter name must not be empty" }
        }
        queryParameters.addAll(parameters.map { it.key to it.value })
    }

    /** Appends query pairs in order, preserving repeated names and letting OkHttp encode each value. */
    fun query(vararg parameters: Pair<String, Any>) {
        queryParameters.addAll(parameters)
    }

    /** Appends one query value rather than replacing earlier values with the same name. */
    fun query(key: String, value: Any) {
        queryParameters.add(key to value)
    }

    // ----------------------------------------------------------------

    /** Adds path substitutions; later values replace earlier values for the same placeholder name. */
    fun path(pathVariables: Map<String, Any>) {
        pathVariables.forEach { (key, value) ->
            require(key.isNotEmpty()) { "Path variables name must not be empty" }
        }
        pathMap.putAll(pathVariables)
    }

    fun path(vararg pathVariables: Pair<String, Any>) {
        pathVariables.forEach { (key, value) ->
            pathMap[key] = value
        }
    }

    fun path(key: String, value: Any) {
        pathMap[key] = value
    }

    // ----------------------------------------------------------------

    /**
     * Builds the OkHttp request and applies request customizations last.
     * Missing path values and invalid OkHttp inputs fail here; form/multipart headers are added to this builder.
     */
    fun build(): Request {
        applyAdditionalHeaders()
        val builder = Request.Builder()
            .headers(buildHeaders(headers))
            .url(createUrl())
            .method(method, createBody(body))

        requestBuilderTweaks.forEach { configure ->
            builder.configure()
        }

        return builder.build()
    }

    private fun applyAdditionalHeaders() {
        val body = this.body
        when (body) {
            is FormDataBody -> header(
                "Content-Type",
                "application/x-www-form-urlencoded"
            )

            is io.github.photowey.kttp.dsl.MultipartBody -> header(
                "Content-Type",
                "multipart/form-data; boundary=${body.boundary}"
            )

            else -> {

            }
        }

        if ("User-Agent" !in headers.map { it.first }) {
            header("User-Agent", UserAgentProvider.userAgent())
        }
    }

    private fun createBody(body: io.github.photowey.kttp.dsl.RequestBody?) = when {
        body != null -> body.toOkHttpBody()
        HttpMethod.requiresRequestBody(method) -> ByteArray(0).toRequestBody()
        else -> null
    }

    private fun createUrl(): HttpUrl {
        val normalizedPath = path.replacePathParams()
        return normalizedPath
            .toHttpUrl()
            .newBuilder()
            .also { urlBuilder ->
                queryParameters.forEach { (name, value) ->
                    urlBuilder.addQueryParameter(name, value.toString())
                }
            }
            .build()
    }

    /** Substitutes placeholder text before URL parsing; values are not separately encoded as path segments. */
    private fun String.replacePathParams(): String {
        return replace(PATH_VARIABLE_REGEX) { matchResult ->
            val pathVariable = matchResult.groupValues[1]
            pathMap.getOrElse(pathVariable) {
                throw MissingPathParameterException(pathVariable)
            }.toString()
        }
    }

    /** Returns client configuration that applies cookies, redirect and protocol flags, then client customizations. */
    fun getClientConfigurer(): ClientConfigurer = {
        if (!noCookies) {
            val cookieJar = context?.cookiesContext?.cookieJar
            if (cookieJar != null) {
                cookieJar(cookieJar)
            }
        }

        followRedirects(!noRedirect)
        followSslRedirects(!noRedirect)

        if (http2) {
            protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
        }

        clientBuilderTweaks.forEach { configure ->
            configure()
        }
    }
}
