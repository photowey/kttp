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
import kotlin.test.assertTrue

/**
 * Verifies projection classifications for response-processing chains and custom request declarations.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class RequestProjectionSendOnlyPsiTest {

    @Test
    fun thenBlockMakesProjectionSendOnly() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            GET("https://example.com/users") {
                header("Accept", "application/json")
            }.then {
                assertStatus(200)
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.SEND_ONLY)
        RequestProjectionTestAssertions.assertHasGap(projection, GapKind.POST_PROCESSING_ONLY)
    }

    @Test
    fun chainedPostProcessingCallsRemainSendOnly() {
        for (call in listOf("thenResponse { println(it) }", "decode<String>()", "jsonPath(\"$.name\")")) {
            val projection = PsiScriptTestSupport.extractFirstProjection(
                "GET(\"https://example.com/users\") { header(\"Accept\", \"application/json\") }.$call"
            )

            RequestProjectionTestAssertions.assertMode(projection, GuiMode.SEND_ONLY)
            RequestProjectionTestAssertions.assertHasGap(projection, GapKind.POST_PROCESSING_ONLY)
        }
    }

    @Test
    fun nestedPostProcessingChainRemainsSendOnly() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            GET("https://example.com/users") {
                header("Accept", "application/json")
            }.then { assertStatus(200) }.thenResponse { println(it) }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.SEND_ONLY)
        RequestProjectionTestAssertions.assertHasGap(projection, GapKind.POST_PROCESSING_ONLY)
    }

    @Test
    fun unknownChainedCallRemainsScriptOnly() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """GET("https://example.com/users") { header("Accept", "application/json") }.customTransform()"""
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.SCRIPT_ONLY)
        RequestProjectionTestAssertions.assertHasGap(projection, GapKind.REQUEST_SHAPING_UNSUPPORTED)
    }

    @Test
    fun customRequestEntryIsSendOnly() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            request("GET", "https://example.com/users") {
                header("Accept", "application/json")
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.SEND_ONLY)
        RequestProjectionTestAssertions.assertHasGap(projection, GapKind.PARTIALLY_PROJECTED)
    }

    @Test
    fun customRequestWithPropertyUrlStaysSendOnly() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            val baseUrl = "https://example.com/users"
            request("POST", baseUrl) {
                header("Accept", "application/json")
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.SEND_ONLY)
        RequestProjectionTestAssertions.assertHasGap(projection, GapKind.PARTIALLY_PROJECTED)
        assertTrue(projection.spec?.target?.url is ValueRef.Literal)
    }

    @Test
    fun customRequestWithEnvUrlStaysSendOnly() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            val baseUrl: String by env
            request("GET", baseUrl) {
                header("Accept", "application/json")
            }
            """.trimIndent()
        )

        RequestProjectionTestAssertions.assertMode(projection, GuiMode.SEND_ONLY)
        RequestProjectionTestAssertions.assertHasGap(projection, GapKind.PARTIALLY_PROJECTED)
        assertTrue(projection.spec?.target?.url is ValueRef.Env)
    }
}
