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

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Shares assertions for projection modes, semantic gaps, and literal URL values.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal object RequestProjectionTestAssertions {

    fun assertMode(projection: RequestProjection, expected: GuiMode) {
        assertEquals(expected, projection.mode)
    }

    fun assertHasGap(projection: RequestProjection, kind: GapKind) {
        assertTrue(projection.gaps.any { it.kind == kind }, "Expected gap kind $kind but got ${projection.gaps}")
    }

    fun assertLiteralUrl(projection: RequestProjection, expected: String) {
        val url = assertNotNull(projection.spec).target.url
        assertTrue(url is ValueRef.Literal)
        assertEquals(expected, url.value)
    }
}
