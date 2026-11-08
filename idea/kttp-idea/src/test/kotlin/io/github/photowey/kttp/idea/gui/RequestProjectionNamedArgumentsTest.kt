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

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Verifies named-argument binding and conservative handling of invalid request argument lists.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class RequestProjectionNamedArgumentsTest {

    @Test
    fun reorderedRequestArgumentsKeepTheActualPathAndDecodedName() {
        val projection = extract(
            """GET(name = "a \"quoted\" request", path = "https://example.com/users") {}"""
        )

        assertEquals(GuiMode.GUI_READY, projection.mode)
        assertEquals(ValueRef.Literal("https://example.com/users"), projection.spec?.target?.url)
        assertEquals("a \"quoted\" request", projection.spec?.identity?.name)
    }

    @Test
    fun reorderedCustomRequestArgumentsKeepMethodAndPath() {
        val projection = extract(
            """request(name = "request", path = "https://example.com/users", method = "POST") {}"""
        )

        assertEquals(GuiMode.SEND_ONLY, projection.mode)
        assertEquals("POST", projection.spec?.method?.value)
        assertEquals(ValueRef.Literal("https://example.com/users"), projection.spec?.target?.url)
    }

    @Test
    fun reorderedNamedFieldsKeepKeysAndValues() {
        val projection = extract(
            """
            POST("https://example.com/{id}") {
                header(value = "header-value", key = "X-Test")
                query(value = "query-value", key = "q")
                path(value = "42", key = "id")
                formData { field(value = "form-value", name = "f") }
            }
            """.trimIndent()
        )

        assertEquals(GuiMode.GUI_READY, projection.mode)
        assertEquals(listOf(HeaderSpec("X-Test", ValueRef.Literal("header-value"))), projection.spec?.headers)
        assertEquals(listOf(NameValueRef("q", ValueRef.Literal("query-value"))), projection.spec?.target?.queryParams)
        assertEquals(listOf(NameValueRef("id", ValueRef.Literal("42"))), projection.spec?.target?.pathParams)
        assertEquals(
            BodySpec.FormUrlEncoded(listOf(NameValueRef("f", ValueRef.Literal("form-value")))),
            projection.spec?.body
        )
    }

    @Test
    fun reorderedBasicAuthKeepsUsernameAndPassword() {
        val projection = extract(
            """GET("https://example.com") { basicAuth(password = "secret", username = "alice") }"""
        )

        assertEquals(GuiMode.GUI_READY, projection.mode)
        assertEquals(AuthSpec.Basic(ValueRef.Literal("alice"), ValueRef.Literal("secret")), projection.spec?.auth)
    }

    @Test
    fun configureArgumentCanPrecedePath() {
        val projection = extract(
            """POST(configure = { header("X-Test", "value") }, path = "https://example.com", name = "request")"""
        )

        assertEquals(GuiMode.GUI_READY, projection.mode)
        assertEquals(ValueRef.Literal("https://example.com"), projection.spec?.target?.url)
        assertEquals(listOf(HeaderSpec("X-Test", ValueRef.Literal("value"))), projection.spec?.headers)
    }

    @Test
    fun unknownOrDuplicateNamedParametersAreNotGuessed() {
        for (source in listOf(
            """GET(url = "https://example.com") {}""",
            """GET("https://example.com") { header(unknown = "X-Test", value = "value") }""",
            """GET("https://example.com") { query(key = "q", key = "duplicate", value = "v") }""",
            """GET("https://example.com") { noRedirect(unexpected = true) }""",
            """GET("https://example.com") { accept(unexpected = "application/json") }"""
        )) {
            val projection = extract(source)
            assertEquals(GuiMode.SCRIPT_ONLY, projection.mode)
            assertNull(projection.spec)
        }
    }

    @Test
    fun referencedConfigureLambdaIsNotSilentlyIgnored() {
        val projection = extract(
            """
            val config: RequestBuilder.() -> Unit = { header("X-Test", "value") }
            GET("https://example.com", configure = config)
            """.trimIndent()
        )

        assertEquals(GuiMode.SCRIPT_ONLY, projection.mode)
        assertNull(projection.spec)
    }

    @Test
    fun positionalNameAndNamedBodyArePreserved() {
        val projection = extract(
            """POST("https://example.com", "request") { body(body = "plain text") }"""
        )

        assertEquals(GuiMode.GUI_READY, projection.mode)
        assertEquals("request", projection.spec?.identity?.name)
        assertEquals(BodySpec.Raw(ValueRef.Literal("plain text")), projection.spec?.body)
    }

    private fun extract(source: String): RequestProjection = PsiScriptTestSupport.extractFirstProjection(source)
}
