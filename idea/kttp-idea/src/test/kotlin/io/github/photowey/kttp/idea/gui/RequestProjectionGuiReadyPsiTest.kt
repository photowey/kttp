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
import kotlin.test.assertTrue

/**
 * Verifies projection of supported literal, environment, file, and simple DSL request values.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class RequestProjectionGuiReadyPsiTest {

    @Test
    fun literalUrlAndSimpleDslAreGuiReady() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            GET("https://example.com/users") {
                query("page", "1")
                header("Accept", "application/json")
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.GUI_READY)
        RequestProjectionTestAssertions.assertLiteralUrl(projection, "https://example.com/users")
    }

    @Test
    fun propertyLiteralReferenceIsProjectedAsGuiReady() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            val baseUrl = "https://example.com/users"
            GET(baseUrl) {
                query("page", "1")
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.GUI_READY)
        RequestProjectionTestAssertions.assertLiteralUrl(projection, "https://example.com/users")
    }

    @Test
    fun envAndLocalFileReferencesStayProjected() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            val baseUrl: String by env
            val payload by localFile("payload.json")
            POST(baseUrl) {
                body(payload)
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.GUI_READY)
        assertTrue(projection.spec?.target?.url is ValueRef.Env)
        assertTrue(projection.spec?.body is BodySpec.Raw)
    }

    @Test
    fun headersCallWithEnvReferenceStaysProjected() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            val token: String by env
            GET("https://example.com/users") {
                headers("Authorization" to token, "Accept" to "application/json")
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.GUI_READY)
        assertEquals(2, projection.spec?.headers?.size)
        assertTrue(projection.spec?.headers?.first { it.name == "Authorization" }?.value is ValueRef.Env)
    }

    @Test
    fun formDataWithEnvFieldStaysProjected() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            val userId: String by env
            POST("https://example.com/users") {
                formData {
                    field("userId", userId)
                    field("role", "admin")
                }
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.GUI_READY)
        assertTrue(projection.spec?.body is BodySpec.FormUrlEncoded)
        val fields = (projection.spec?.body as BodySpec.FormUrlEncoded).fields
        assertTrue(fields.first { it.name == "userId" }.value is ValueRef.Env)
    }

    @Test
    fun multiLevelPropertyReferenceStaysProjected() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            val host = "https://example.com"
            val baseUrl = host
            GET(baseUrl) {
                query("page", "1")
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.GUI_READY)
        RequestProjectionTestAssertions.assertLiteralUrl(projection, "https://example.com")
    }

    @Test
    fun localFileInlineCallInBodyStaysProjected() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            POST("https://example.com/users") {
                body(localFile("payload.json"))
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.GUI_READY)
        val body = projection.spec?.body as BodySpec.Raw
        assertTrue(body.content is ValueRef.LocalFile)
        assertEquals("payload.json", (body.content as ValueRef.LocalFile).path)
    }

    @Test
    fun contentTypeAndAcceptWithEnvReferencesStayProjected() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            val acceptType: String by env
            val contentTypeValue: String by env
            POST("https://example.com/users") {
                contentType(contentTypeValue)
                accept(acceptType)
                body("{}")
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.GUI_READY)
        val headers = projection.spec?.headers.orEmpty()
        assertTrue(headers.first { it.name == "Content-Type" }.value is ValueRef.Env)
        assertTrue(headers.first { it.name == "Accept" }.value is ValueRef.Env)
    }

    @Test
    fun basicAuthWithEnvReferencesStaysProjected() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            val username: String by env
            val password: String by env
            GET("https://example.com/profile") {
                basicAuth(username, password)
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.GUI_READY)
        val auth = projection.spec?.auth as AuthSpec.Basic
        assertTrue(auth.username is ValueRef.Env)
        assertTrue(auth.password is ValueRef.Env)
    }

    @Test
    fun literalAliasInHeadersListStaysProjected() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            val acceptJson = "application/json"
            GET("https://example.com/users") {
                headers("Accept" to acceptJson)
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.GUI_READY)
        val header = projection.spec?.headers?.single() ?: error("expected one header")
        assertEquals("Accept", header.name)
        assertEquals("application/json", (header.value as ValueRef.Literal).value)
    }

    @Test
    fun envAliasChainInHeaderStaysProjected() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            val envToken: String by env
            val tokenAlias = envToken
            GET("https://example.com/users") {
                header("Authorization", tokenAlias)
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.GUI_READY)
        val header = projection.spec?.headers?.single() ?: error("expected one header")
        assertTrue(header.value is ValueRef.Env)
        assertEquals("envToken", (header.value as ValueRef.Env).key)
    }

    @Test
    fun localFileAliasChainInBodyStaysProjected() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            val payloadFile by localFile("payload.json")
            val payloadAlias = payloadFile
            POST("https://example.com/users") {
                body(payloadAlias)
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.GUI_READY)
        val body = projection.spec?.body as BodySpec.Raw
        assertTrue(body.content is ValueRef.LocalFile)
        assertEquals("payload.json", (body.content as ValueRef.LocalFile).path)
    }

    @Test
    fun formDataWithLiteralAliasAndEnvAliasStaysProjected() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            val envUserId: String by env
            val userId = envUserId
            val role = "admin"
            POST("https://example.com/users") {
                formData {
                    field("userId", userId)
                    field("role", role)
                }
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.GUI_READY)
        val body = projection.spec?.body as BodySpec.FormUrlEncoded
        assertTrue(body.fields.first { it.name == "userId" }.value is ValueRef.Env)
        assertTrue(body.fields.first { it.name == "role" }.value is ValueRef.Literal)
    }

    @Test
    fun pathWithEnvAliasStaysProjected() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            val envOrderId: String by env
            val orderId = envOrderId
            GET("https://example.com/orders/{id}") {
                path("id", orderId)
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.GUI_READY)
        val pathParam = projection.spec?.target?.pathParams?.single() ?: error("expected one path param")
        assertEquals("id", pathParam.name)
        assertTrue(pathParam.value is ValueRef.Env)
    }

    @Test
    fun queryWithLiteralAliasStaysProjected() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            val page = "1"
            GET("https://example.com/users") {
                query("page", page)
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.GUI_READY)
        val queryParam = projection.spec?.target?.queryParams?.single() ?: error("expected one query param")
        assertEquals("page", queryParam.name)
        assertEquals("1", (queryParam.value as ValueRef.Literal).value)
    }

    @Test
    fun contentTypeWithLiteralAliasStaysProjected() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            val jsonType = "application/json"
            POST("https://example.com/users") {
                contentType(jsonType)
                body("{}")
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.GUI_READY)
        val contentType = projection.spec?.headers?.first { it.name == "Content-Type" }
            ?: error("expected content-type header")
        assertEquals("application/json", (contentType.value as ValueRef.Literal).value)
    }
}
