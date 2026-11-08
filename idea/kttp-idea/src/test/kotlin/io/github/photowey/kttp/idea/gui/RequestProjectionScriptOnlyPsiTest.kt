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

/**
 * Verifies that unsupported expressions and request-shaping operations require script execution.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class RequestProjectionScriptOnlyPsiTest {

    @Test
    fun stringInterpolationMakesProjectionScriptOnly() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            val baseUrl = "https://example.com"
            GET("${'$'}baseUrl/users") {
                query("page", "1")
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.SCRIPT_ONLY)
        RequestProjectionTestAssertions.assertHasGap(projection, GapKind.SCRIPT_EXPRESSION_UNSUPPORTED)
    }

    @Test
    fun configureRequestIsScriptOnly() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            GET("https://example.com/users") {
                configureRequest {
                    addHeader("X-Debug", "1")
                }
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.SCRIPT_ONLY)
        RequestProjectionTestAssertions.assertHasGap(projection, GapKind.REQUEST_SHAPING_UNSUPPORTED)
    }

    @Test
    fun headersMapExpressionIsScriptOnly() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            GET("https://example.com/users") {
                headers(mapOf("Accept" to "application/json"))
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.SCRIPT_ONLY)
        RequestProjectionTestAssertions.assertHasGap(projection, GapKind.REQUEST_SHAPING_UNSUPPORTED)
    }

    @Test
    fun queryWithFunctionCallIsScriptOnly() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            fun buildValue(): String = "1"
            GET("https://example.com/users") {
                query("page", buildValue())
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.SCRIPT_ONLY)
        RequestProjectionTestAssertions.assertHasGap(projection, GapKind.REQUEST_SHAPING_UNSUPPORTED)
    }

    @Test
    fun bodyWithFunctionCallIsScriptOnly() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            fun buildPayload(): String = "{}"
            POST("https://example.com/users") {
                body(buildPayload())
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.SCRIPT_ONLY)
        RequestProjectionTestAssertions.assertHasGap(projection, GapKind.REQUEST_SHAPING_UNSUPPORTED)
    }

    @Test
    fun acceptWithStringInterpolationIsScriptOnly() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            val type = "application"
            GET("https://example.com/users") {
                accept("${'$'}type/json")
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.SCRIPT_ONLY)
        RequestProjectionTestAssertions.assertHasGap(projection, GapKind.REQUEST_SHAPING_UNSUPPORTED)
    }

    @Test
    fun basicAuthWithFunctionCallsIsScriptOnly() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            fun user(): String = "alice"
            fun pass(): String = "secret"
            GET("https://example.com/profile") {
                basicAuth(user(), pass())
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.SCRIPT_ONLY)
        RequestProjectionTestAssertions.assertHasGap(projection, GapKind.REQUEST_SHAPING_UNSUPPORTED)
    }

    @Test
    fun headersListWithLocalFileValueIsScriptOnly() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            GET("https://example.com/users") {
                headers("X-Payload" to localFile("payload.txt"))
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.SCRIPT_ONLY)
        RequestProjectionTestAssertions.assertHasGap(projection, GapKind.REQUEST_SHAPING_UNSUPPORTED)
    }

    @Test
    fun customRequestWithInterpolatedUrlIsScriptOnly() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            val host = "https://example.com"
            request("POST", "${'$'}host/users") {
                header("Accept", "application/json")
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.SCRIPT_ONLY)
        RequestProjectionTestAssertions.assertHasGap(projection, GapKind.SCRIPT_EXPRESSION_UNSUPPORTED)
    }
}
