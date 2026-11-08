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

package io.github.photowey.kttp.dsl

import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.TypeRef
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import io.github.photowey.kttp.core.annotation.KttpDSL
import io.github.photowey.kttp.core.context.defaultObjectMapper
import io.github.photowey.kttp.core.context.execution.result.MappedRequestHolder
import io.github.photowey.kttp.core.context.execution.result.RequestHolder
import okhttp3.Response

/**
 * Receiver for `then` callbacks, providing assertions and response value extraction.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/17
 */
@KttpDSL
class ThenContext(internal val response: Response) {

    val statusCode: Int get() = response.code
    val headers: Map<String, String> by lazy {
        response.headers.toMultimap()
            .mapValues { it.value.joinToString(", ") }
    }

    /** Cached response text; the first access consumes the underlying response body. */
    val body: String by lazy {
        response.body?.string() ?: ""
    }

    /**
     * Parse response body as JSON and return JsonPath context.
     */
    fun jsonPath(): JsonPathContext = JsonPathContext(body)

    /**
     * Decode a value from JSON response using JsonPath.
     *
     * @param path JsonPath expression (default: "$" for root)
     * @return Decoded value of type T
     *
     * Example:
     * ```kotlin
     * POST("https://api.example.com/users").then {
     *     val name: String = decode("$.name")
     *     val id: Long = decode("$.id")
     * }
     * ```
     */
    inline fun <reified T> decode(path: String = "$"): T {
        return jsonPath().doRead<T>(path)
    }

    /**
     * Get the underlying Response object for direct access.
     * Access its properties through the returned object, for example
     * `assertThat(getResponse().code).isEqualTo(200)`; the `then` receiver remains [ThenContext].
     */
    fun getResponse(): Response = response

    fun response(): Response = getResponse()

    /**
     * Assert that response status code equals expected value.
     */
    fun assertStatus(expected: Int) {
        if (statusCode != expected) {
            throw AssertionError("Expected status $expected but got $statusCode")
        }
    }

    /**
     * Assert that response body contains expected string.
     */
    fun assertBodyContains(expected: String) {
        if (!body.contains(expected)) {
            throw AssertionError("Expected body to contain '$expected'")
        }
    }

    /**
     * Assert that response header equals expected value.
     * Uses OkHttp's header(name) which is case-insensitive (HTTP spec).
     */
    fun assertHeader(name: String, expected: String) {
        val actual = response.header(name)
        if (actual != expected) {
            throw AssertionError("Expected header '$name' to be '$expected' but was '$actual'")
        }
    }
}

/** Jackson-based JsonPath config so TypeRef is supported (json-smart does not support TypeRef). */
@PublishedApi
internal val jsonPathConfig: Configuration by lazy {
    Configuration.builder()
        .jsonProvider(JacksonJsonProvider(defaultObjectMapper))
        .mappingProvider(JacksonMappingProvider(defaultObjectMapper))
        .build()
}

/**
 * JsonPath context for extracting values from JSON response.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/17
 */
@KttpDSL
class JsonPathContext(val json: String) {

    /**
     * Read a value of type T from JSON using JsonPath with type safety.
     * Uses Jackson provider so TypeRef is supported.
     */
    inline fun <reified T> doRead(path: String): T {
        val readContext = JsonPath.parse(json, jsonPathConfig)
        return readContext.read(path, object : TypeRef<T>() {})
    }

    /**
     * Read a string value from JSON using JsonPath.
     */
    fun readString(path: String): String {
        return JsonPath.parse(json, jsonPathConfig).read(path)
    }

    /**
     * Read an integer value from JSON using JsonPath.
     */
    fun readInt(path: String): Int {
        return JsonPath.parse(json, jsonPathConfig).read(path)
    }

    /**
     * Read a long value from JSON using JsonPath.
     */
    fun readLong(path: String): Long {
        return JsonPath.parse(json, jsonPathConfig).read(path)
    }

    /**
     * Read a double value from JSON using JsonPath.
     */
    fun readDouble(path: String): Double {
        return JsonPath.parse(json, jsonPathConfig).read(path)
    }

    /**
     * Read a boolean value from JSON using JsonPath.
     */
    fun readBoolean(path: String): Boolean {
        return JsonPath.parse(json, jsonPathConfig).read(path)
    }

    /**
     * Read a list of values from JSON using JsonPath.
     */
    fun <T> readList(path: String): List<T> {
        return JsonPath.parse(json, jsonPathConfig).read(path)
    }
}

/**
 * Extension function to support `then` block on RequestHolder with ThenContext receiver.
 * This allows using ThenContext methods like assertStatus(), getResponse(), decode(), etc.
 * Usage:
 * ```kotlin
 * POST("https://api.example.com/users").then {
 *     assertStatus(200)
 *     val name: String = decode("$.name")
 * }
 * ```
 *
 * The block receives [ThenContext]. For a [Response] receiver, use `thenResponse { }` instead.
 */
inline fun <T> RequestHolder.then(crossinline block: ThenContext.() -> T): MappedRequestHolder<T> {
    return this.mapWithThen {
        val ctx = ThenContext(this)  // 'this' is Response
        block(ctx)
    }
}

/**
 * Runs a block with [ThenContext] against a response already received by this holder.
 * Unlike the [RequestHolder] extension, this does not initiate execution or create a mapped holder.
 *
 * @throws IllegalStateException if this holder has not received a response
 */
inline fun <T> MappedRequestHolder<T>.then(block: ThenContext.() -> T): T {
    val response = this.getLastResponse()
        ?: throw IllegalStateException("No response available")
    val context = ThenContext(response)
    return block(context)
}
