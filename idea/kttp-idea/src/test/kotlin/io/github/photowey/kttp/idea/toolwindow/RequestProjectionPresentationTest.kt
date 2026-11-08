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

import io.github.photowey.kttp.idea.gui.GuiMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies capability badges, file summaries, sidebar filters, and sorting priorities.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class RequestProjectionPresentationTest {

    @Test
    fun completeProjectionShowsQuickAvailabilityAndExplainsDetach() {
        val badge = RequestProjectionPresentation.badge(GuiMode.GUI_READY)
        assertEquals("Quick available", badge.text)
        assertTrue(badge.tooltip.contains("Detach"))
        assertTrue(badge.tooltip.contains("Follow Script"))
    }

    @Test
    fun partialProjectionUsesTheSameQuickAvailabilityLabel() {
        val badge = RequestProjectionPresentation.badge(GuiMode.SEND_ONLY)
        assertEquals("Quick available", badge.text)
        assertTrue(badge.tooltip.contains("script processing"))
        assertTrue(badge.tooltip.contains("Follow Script"))
    }

    @Test
    fun scriptRequiredBadgeExplainsHowToRunFromTheComposer() {
        val badge = RequestProjectionPresentation.badge(GuiMode.SCRIPT_ONLY)
        assertEquals("Script required", badge.text)
        assertTrue(badge.tooltip.contains("Follow Script"))
    }

    @Test
    fun fileSummaryCountsModes() {
        val summary = RequestProjectionPresentation.summary(
            listOf(GuiMode.GUI_READY, GuiMode.GUI_READY, GuiMode.SEND_ONLY, GuiMode.SCRIPT_ONLY)
        )

        assertEquals(2, summary.guiReadyCount)
        assertEquals(1, summary.sendOnlyCount)
        assertEquals(1, summary.scriptOnlyCount)
    }

    @Test
    fun mixedFileShowsTotalCountAndReadableCapabilityTooltip() {
        val badge = RequestProjectionPresentation.fileBadge(
            FileProjectionSummary(guiReadyCount = 2, sendOnlyCount = 1, scriptOnlyCount = 1)
        )

        assertEquals("4 requests", badge.text)
        assertTrue(badge.tooltip.contains("Quick available: 3"))
        assertTrue(badge.tooltip.contains("Script required: 1"))
        assertTrue(!badge.tooltip.contains("Legend"))
    }

    @Test
    fun fileCountsUseTheSameFormatForEmptySingleAndUniformFiles() {
        val cases = listOf(
            FileProjectionSummary() to "0 requests",
            FileProjectionSummary(guiReadyCount = 1) to "1 request",
            FileProjectionSummary(sendOnlyCount = 2) to "2 requests",
            FileProjectionSummary(scriptOnlyCount = 3) to "3 requests"
        )
        cases.forEach { (summary, label) ->
            assertEquals(label, RequestProjectionPresentation.fileBadge(summary).text)
        }
    }

    @Test
    fun filterRulesMatchExpectedSummaryBuckets() {
        val gui = FileProjectionSummary(guiReadyCount = 2)
        val send = FileProjectionSummary(sendOnlyCount = 1)
        val script = FileProjectionSummary(scriptOnlyCount = 3)
        val mixed = FileProjectionSummary(guiReadyCount = 1, scriptOnlyCount = 1)

        assertTrue(RequestProjectionPresentation.matchesFilter(gui, FileProjectionFilter.HAS_GUI))
        assertTrue(RequestProjectionPresentation.matchesFilter(send, FileProjectionFilter.HAS_SEND))
        assertTrue(RequestProjectionPresentation.matchesFilter(script, FileProjectionFilter.ONLY_SCRIPT))
        assertTrue(RequestProjectionPresentation.matchesFilter(mixed, FileProjectionFilter.MIXED))
    }

    @Test
    fun sortWeightPrioritizesGuiThenSendThenScript() {
        assertEquals(0, RequestProjectionPresentation.sortWeight(FileProjectionSummary(guiReadyCount = 1)))
        assertEquals(1, RequestProjectionPresentation.sortWeight(FileProjectionSummary(sendOnlyCount = 1)))
        assertEquals(2, RequestProjectionPresentation.sortWeight(FileProjectionSummary(scriptOnlyCount = 1)))
        assertEquals(3, RequestProjectionPresentation.sortWeight(FileProjectionSummary()))
    }
}
