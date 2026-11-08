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

import io.github.photowey.kttp.core.enums.HttpMethod
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Verifies capability classification from projected request specifications and semantic gaps.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class RequestProjectionExtractorTest {

    private val spec = RequestSpec(
        method = HttpMethod.GET,
        target = RequestTarget(ValueRef.Literal("https://example.com"))
    )

    @Test
    fun determineModeReturnsGuiReadyWhenNoGapsExist() {
        assertEquals(GuiMode.GUI_READY, RequestProjectionExtractor.determineMode(spec, emptyList()))
    }

    @Test
    fun determineModeReturnsSendOnlyForPostProcessingGaps() {
        val gaps = listOf(
            SemanticGap(GapKind.POST_PROCESSING_ONLY, "then", "then will be skipped")
        )
        assertEquals(GuiMode.SEND_ONLY, RequestProjectionExtractor.determineMode(spec, gaps))
    }

    @Test
    fun determineModeReturnsSendOnlyForPartialProjectionGaps() {
        val gaps = listOf(
            SemanticGap(GapKind.PARTIALLY_PROJECTED, "request", "Imported through custom request entrypoint")
        )
        assertEquals(GuiMode.SEND_ONLY, RequestProjectionExtractor.determineMode(spec, gaps))
    }

    @Test
    fun determineModeReturnsScriptOnlyForBlockingGaps() {
        val gaps = listOf(
            SemanticGap(GapKind.REQUEST_SHAPING_UNSUPPORTED, "configureRequest", "Unsupported")
        )
        assertEquals(GuiMode.SCRIPT_ONLY, RequestProjectionExtractor.determineMode(spec, gaps))
    }

    @Test
    fun determineModeReturnsScriptOnlyWhenSpecIsMissing() {
        assertEquals(GuiMode.SCRIPT_ONLY, RequestProjectionExtractor.determineMode(null, emptyList()))
    }
}
