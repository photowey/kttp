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

package io.github.photowey.kttp.script

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Verifies the lightweight parser's method and URL text matching behavior.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpScriptParserTest {

    @Test
    fun `parseFirstRequest GET single line returns method and url`() {
        val r = KttpScriptParser.parseFirstRequest("""GET("https://httpbin.org/get") { }""")
        assertEquals("GET", r?.method)
        assertEquals("https://httpbin.org/get", r?.url)
    }

    @Test
    fun `parseFirstRequest POST with braces returns method and url`() {
        val r = KttpScriptParser.parseFirstRequest(
            """
            POST("https://api.example.com") {
                body(jsonBody)
            }
        """.trimIndent()
        )
        assertEquals("POST", r?.method)
        assertEquals("https://api.example.com", r?.url)
    }

    @Test
    fun `parseFirstRequest single quotes url is supported`() {
        val r = KttpScriptParser.parseFirstRequest("""GET('https://example.com')""")
        assertEquals("GET", r?.method)
        assertEquals("https://example.com", r?.url)
    }

    @Test
    fun `parseFirstRequest comment only returns null`() {
        assertNull(KttpScriptParser.parseFirstRequest("// comment only"))
    }

    @Test
    fun `parseFirstRequest no request expression returns null`() {
        assertNull(KttpScriptParser.parseFirstRequest("val x = 1"))
    }

    @Test
    fun `parseFirstRequest PUT PATCH DELETE HEAD OPTIONS are matched`() {
        assertEquals("PUT", KttpScriptParser.parseFirstRequest("""PUT("https://api.example.com/1")""")?.method)
        assertEquals("PATCH", KttpScriptParser.parseFirstRequest("""PATCH("https://api.example.com/2")""")?.method)
        assertEquals("DELETE", KttpScriptParser.parseFirstRequest("""DELETE("https://api.example.com/3")""")?.method)
        assertEquals("HEAD", KttpScriptParser.parseFirstRequest("""HEAD("https://api.example.com/4")""")?.method)
        assertEquals("OPTIONS", KttpScriptParser.parseFirstRequest("""OPTIONS("https://api.example.com/5")""")?.method)
    }

    @Test
    fun `parseFirstRequest returns first request when multiple present`() {
        val r = KttpScriptParser.parseFirstRequest(
            """
            GET("https://first.org")
            POST("https://second.org") { }
        """.trimIndent()
        )
        assertEquals("GET", r?.method)
        assertEquals("https://first.org", r?.url)
    }

    @Test
    fun `parseFirstRequest url with spaces around parentheses is trimmed`() {
        val r = KttpScriptParser.parseFirstRequest("""GET( "https://example.com" )""")
        assertEquals("GET", r?.method)
        assertEquals("https://example.com", r?.url)
    }

    @Test
    fun `parseFirstRequest relative path is returned as url`() {
        val r = KttpScriptParser.parseFirstRequest("""GET("/api/users") { }""")
        assertEquals("GET", r?.method)
        assertEquals("/api/users", r?.url)
    }

    @Test
    fun `parseFirstRequest template expression in first request returns literal pattern`() {
        val dollar = '\u0024'
        val script = "val baseUrl by env\nGET(\"${dollar}baseUrl/api/foo\")"
        val r = KttpScriptParser.parseFirstRequest(script)
        assertEquals("GET", r?.method)
        assertEquals("${dollar}baseUrl/api/foo", r?.url)
    }

    @Test
    fun `parseFirstRequest returns first request when three requests present`() {
        val brace = "{}"
        val script = "GET(\"https://first.org\")\nPOST(\"https://second.org\") $brace\nGET(\"https://third.org/api\")"
        val r = KttpScriptParser.parseFirstRequest(script)
        assertEquals("GET", r?.method)
        assertEquals("https://first.org", r?.url)
    }

    @Test
    fun `parseFirstRequest URL with query string is captured as url`() {
        val r = KttpScriptParser.parseFirstRequest("""GET("https://api.example.com/search?q=hello&limit=10")""")
        assertEquals("GET", r?.method)
        assertEquals("https://api.example.com/search?q=hello&limit=10", r?.url)
    }

    @Test
    fun `parseFirstRequest empty string returns null`() {
        assertNull(KttpScriptParser.parseFirstRequest(""))
    }

    @Test
    fun `parseFirstRequest only whitespace and newlines returns null`() {
        assertNull(KttpScriptParser.parseFirstRequest("   \n  \t\n "))
    }

    @Test
    fun `parseFirstRequest script with leading comment then GET returns first request`() {
        val r = KttpScriptParser.parseFirstRequest("// setup\nGET(\"https://api.example.com\")")
        assertEquals("GET", r?.method)
        assertEquals("https://api.example.com", r?.url)
    }

    @Test
    fun `parseFirstRequest url with path and trailing slash`() {
        val r = KttpScriptParser.parseFirstRequest("""GET("https://example.com/api/v1/")""")
        assertEquals("GET", r?.method)
        assertEquals("https://example.com/api/v1/", r?.url)
    }
}
