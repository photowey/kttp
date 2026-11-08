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

import io.github.photowey.kttp.core.exception.MissingPathParameterException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Verifies request construction, repeated query values, default headers, and validation errors.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class RequestBuilderTest {

    @Test
    fun `build replaces path placeholders and adds query params`() {
        val rb = RequestBuilder(
            "GET",
            "http://localhost/api/{orderId}/{bar-bar}",
            null
        )
        rb.path("orderId", "102")
        rb.path("bar-bar", "2")
        rb.query("q1", 1)
        rb.query("q2", "2")
        val request = rb.build()

        assertEquals("GET", request.method)
        assertEquals("http://localhost/api/102/2?q1=1&q2=2", request.url.toString())
    }

    @Test
    fun `build preserves repeated query names and declaration order`() {
        val rb = RequestBuilder("GET", "http://localhost/api?existing=0&tag=original", null)
        rb.query("tag", "a")
        rb.query("other" to "1", "tag" to "b")
        rb.queries(linkedMapOf("tag" to "c", "last" to "2"))

        val request = rb.build()

        assertEquals(
            "http://localhost/api?existing=0&tag=original&tag=a&other=1&tag=b&tag=c&last=2",
            request.url.toString()
        )
        assertEquals(listOf("original", "a", "b", "c"), request.url.queryParameterValues("tag"))
    }

    @Test
    fun `build retains repeated values within one query call`() {
        val rb = RequestBuilder("GET", "http://localhost/api", null)
        rb.query("tag" to "a", "tag" to "b")

        assertEquals("http://localhost/api?tag=a&tag=b", rb.build().url.toString())
    }

    @Test
    fun `build adds User-Agent when not set`() {
        val rb = RequestBuilder("GET", "http://localhost/", null)
        val request = rb.build()
        val ua = request.header("User-Agent")
        assertTrue(ua != null && ua.startsWith("Kotlin/") && ua.contains("Kttp/"))
    }

    @Test
    fun `build with custom User-Agent keeps it`() {
        val rb = RequestBuilder("GET", "http://localhost/", null)
        rb.header("User-Agent", "Custom/1.0")
        val request = rb.build()
        assertEquals("Custom/1.0", request.header("User-Agent"))
    }

    @Test
    fun `missing path param throws MissingPathParameterException`() {
        val e = assertFailsWith<MissingPathParameterException> {
            val rb = RequestBuilder(
                "GET",
                "http://localhost/api/{foo}/{bar}/{missing}",
                null
            )
            rb.path("foo", "1")
            rb.path("bar", "2")
            rb.build()
        }
        assertEquals("missing", e.pathVariable)
    }

    @Test
    fun `body set twice throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            val rb = RequestBuilder("POST", "http://localhost/", null)
            rb.body("first")
            rb.body("second")
        }
    }

    @Test
    fun `headers with empty name throws`() {
        assertFailsWith<IllegalArgumentException> {
            val rb = RequestBuilder("GET", "http://localhost/", null)
            rb.headers(mapOf("" to "value"))
            rb.build()
        }
    }

    @Test
    fun `queries with empty param name throws`() {
        assertFailsWith<IllegalArgumentException> {
            val rb = RequestBuilder("GET", "http://localhost/", null)
            rb.queries(mapOf("" to "v"))
            rb.build()
        }
    }

    @Test
    fun `path with empty variable name throws`() {
        assertFailsWith<IllegalArgumentException> {
            val rb = RequestBuilder("GET", "http://localhost/{x}", null)
            rb.path(mapOf("" to "v"))
            rb.build()
        }
    }

}
