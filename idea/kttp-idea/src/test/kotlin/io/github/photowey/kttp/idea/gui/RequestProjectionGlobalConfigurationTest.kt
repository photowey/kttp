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
import kotlin.test.assertTrue

/**
 * Verifies that global client configuration and request wrappers cannot be silently dropped by projection.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class RequestProjectionGlobalConfigurationTest {

    @Test
    fun parenthesizedLambdaCallCannotSilentlyDropRequestHeaders() {
        assertScriptOnly(
            """
            GET("https://example.com") { ({ header("X-Required", "yes") })() }
        """
        )
    }

    @Test
    fun configurationBeforeRequestRequiresScriptExecution() {
        assertScriptOnly(
            """
            configureClient { followRedirects(false) }
            GET("https://example.com")
        """
        )
    }

    @Test
    fun configurationAfterRequestRequiresScriptExecution() {
        assertScriptOnly(
            """
            GET("https://example.com")
            configureClient { followRedirects(false) }
        """
        )
    }

    @Test
    fun qualifiedConfigurationRequiresScriptExecution() {
        assertScriptOnly(
            """
            this.configureClient { followRedirects(false) }
            GET("https://example.com")
        """
        )
    }

    @Test
    fun importedConfigurationAliasRequiresScriptExecution() {
        assertScriptOnly(
            """
            import example.configureClient as tuneClient
            tuneClient { followRedirects(false) }
            GET("https://example.com")
        """
        )
    }

    @Test
    fun invokedConfigurationWrapperRequiresScriptExecution() {
        assertScriptOnly(
            """
            fun setTransport() { configureClient { followRedirects(false) } }
            setTransport()
            GET("https://example.com")
        """
        )
    }

    @Test
    fun nestedConfigurationRequiresScriptExecution() {
        assertScriptOnly(
            """
            run { configureClient { followRedirects(false) } }
            GET("https://example.com")
        """
        )
    }

    @Test
    fun callableConfigurationReferenceRequiresScriptExecution() {
        assertScriptOnly(
            """
            val tuneClient = ::configureClient
            tuneClient { followRedirects(false) }
            GET("https://example.com")
        """
        )
    }

    @Test
    fun unrelatedTextDoesNotDowngradeARequest() {
        val projection = PsiScriptTestSupport.extractFirstProjection(
            """
            // configureClient { followRedirects(false) }
            val note = "configureClient"
            GET("https://example.com")
        """.trimIndent()
        )
        assertEquals(GuiMode.GUI_READY, projection.mode)
    }

    private fun assertScriptOnly(source: String) {
        val projection = PsiScriptTestSupport.extractFirstProjection(source.trimIndent())
        assertEquals(GuiMode.SCRIPT_ONLY, projection.mode)
        assertNull(projection.spec)
        assertTrue(projection.gaps.any { it.kind == GapKind.REQUEST_SHAPING_UNSUPPORTED })
    }
}
