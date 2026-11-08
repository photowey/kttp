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
 * Verifies conservative projection decisions for mutable state and order-sensitive authentication.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class RequestProjectionConservativeStateTest {

    @Test
    fun repeatedAuthenticationCallsRemainScriptOnly() {
        for (calls in listOf(
            """bearerAuth("first"); bearerAuth("second")""",
            """basicAuth("alice", "first"); basicAuth("bob", "second")""",
            """bearerAuth("token"); basicAuth("alice", "secret")"""
        )) {
            assertScriptOnly("GET(\"https://example.com\") { $calls }")
        }
    }

    @Test
    fun authorizationHeaderAndAuthenticationCallsAreNeverReordered() {
        for (calls in listOf(
            """bearerAuth("token"); header("Authorization", "other")""",
            """header("authorization", "other"); bearerAuth("token")""",
            """basicAuth("alice", "secret"); headers("AUTHORIZATION" to "other")"""
        )) {
            assertScriptOnly("GET(\"https://example.com\") { $calls }")
        }
    }

    @Test
    fun mutablePropertyIsNotReadAsItsInitialValue() {
        assertScriptOnly(
            """
            var token = "old"
            token = "new"
            GET("https://example.com") { header("X-Token", token) }
            """.trimIndent()
        )
    }

    @Test
    fun aliasToMutablePropertyIsNotReadAsAConstant() {
        assertScriptOnly(
            """
            var token = "old"
            token = "new"
            val alias = token
            GET("https://example.com") { header("X-Token", alias) }
            """.trimIndent()
        )
    }

    @Test
    fun nestedPropertyCannotBeUsedAsATopLevelConstant() {
        assertScriptOnly(
            """
            fun unrelated() { val token = "hidden" }
            GET("https://example.com") { header("X-Token", token) }
            """.trimIndent()
        )
    }

    @Test
    fun duplicatePropertyNamesAreNotResolvedByFirstOccurrence() {
        assertScriptOnly(
            """
            val token = "top-level"
            fun unrelated() { val token = "nested" }
            GET("https://example.com") { header("X-Token", token) }
            """.trimIndent()
        )
    }

    @Test
    fun ambiguousParameterNamesAreNotResolvedToAProperty() {
        assertScriptOnly(
            """
            val token = "top-level"
            fun unrelated(token: String) = token
            GET("https://example.com") { header("X-Token", token) }
            """.trimIndent()
        )
    }

    @Test
    fun getterPropertyIsNotReadAsItsBackingFieldInitializer() {
        assertScriptOnly(
            """
            val token = "old"
                get() = "new"
            GET("https://example.com") { header("X-Token", token) }
            """.trimIndent()
        )
    }

    @Test
    fun uniqueTopLevelValAndOneAuthenticationCallRemainGuiReady() {
        val projection = extract(
            """
            val token = "safe"
            val alias = token
            GET("https://example.com") { bearerAuth(alias); header("X-Test", "value") }
            """.trimIndent()
        )

        assertEquals(GuiMode.GUI_READY, projection.mode)
        assertEquals(AuthSpec.Bearer(ValueRef.Literal("safe")), projection.spec?.auth)
    }

    private fun assertScriptOnly(source: String) {
        val projection = extract(source)
        assertEquals(GuiMode.SCRIPT_ONLY, projection.mode)
        assertNull(projection.spec)
    }

    private fun extract(source: String): RequestProjection = PsiScriptTestSupport.extractFirstProjection(source)
}
