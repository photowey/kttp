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

package io.github.photowey.kttp.idea.toolwindow

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies visible response ordering and preferred sidebar selection targets.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class SidebarPresentationTest {

    @Test
    fun responseListHidesHistoryIndexAndShowsNewestFirst() {
        val entries = listOf(
            SidebarResponseEntry("old", "2026-11-01.json", 10L),
            SidebarResponseEntry("history", "kttp-history.json", 30L),
            SidebarResponseEntry("new", "2026-11-03.json", 30L),
            SidebarResponseEntry("same-time-a", "a.json", 20L),
            SidebarResponseEntry("same-time-b", "b.json", 20L)
        )

        assertEquals(
            listOf("new", "same-time-a", "same-time-b", "old"),
            SidebarPresentation.visibleResponses(entries).map { it.value }
        )
    }

    @Test
    fun allFilterKeepsEmptyScriptsVisible() {
        assertTrue(
            RequestProjectionPresentation.matchesFilter(
                FileProjectionSummary(),
                FileProjectionFilter.ALL
            )
        )
    }

    @Test
    fun pendingSelectionWinsAfterCreatingANewScript() {
        assertEquals(
            "file:/project/kttp/temp/new-request.kttp.kts",
            SidebarPresentation.preferredSelectionKey(
                currentSelectionKey = "draft:existing",
                pendingSelectionKey = "file:/project/kttp/temp/new-request.kttp.kts"
            )
        )
    }
}
