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

import com.intellij.testFramework.LightPlatformTestCase
import io.github.photowey.kttp.idea.io.HistoryPersistence
import io.github.photowey.kttp.idea.path.KttpConsolesPath
import java.io.File
import javax.swing.JList

/**
 * Verifies history synchronization across open windows and protection against corrupt archive replacement.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class HistoryConsistencyTest : LightPlatformTestCase() {
    fun testTwoPreviouslyOpenedWindowsCannotLoseEachOthersEntries() = withHistory {
        val first = HistoryTabPanel(project)
        val second = HistoryTabPanel(project)
        second.addEntry("second-window", "/responses/second.json", "/scripts/second.kttp")
        first.addEntry("first-window", "/responses/first.json", "/scripts/first.kttp")

        assertEquals(listOf("second-window", "first-window"), HistoryPersistence.load().map { it.scriptName })
        assertEquals(listOf("second-window", "first-window"), names(first))
        assertEquals(listOf("second-window", "first-window"), names(second))
    }

    fun testOpenWindowRefreshesWhenAnotherWindowAddsOrClearsHistory() = withHistory {
        val first = HistoryTabPanel(project)
        val second = HistoryTabPanel(project)
        first.addEntry("first", "/responses/first.json", null)
        assertEquals(listOf("first"), names(second))
        second.clear()
        assertTrue(names(first).isEmpty())
        assertTrue(HistoryPersistence.load().isEmpty())
    }

    fun testGuiAppendCannotOverwriteAnEntryPersistedAfterTheWindowOpened() = withHistory {
        val panel = HistoryTabPanel(project)
        HistoryPersistence.append(listOf(HistoryPersistence.Record("run", "/responses/run.json", "/scripts/run.kttp")))
        panel.addEntry("gui", "/responses/gui.json", null)
        assertEquals(listOf("run", "gui"), HistoryPersistence.load().map { it.scriptName })
    }

    fun testCorruptHistoryIsSurfacedAndCannotBeReplacedByGuiAppend() = withHistory {
        val file = File(KttpConsolesPath.getResponsesDir(), "kttp-history.json")
        val panel = HistoryTabPanel(project)
        val corrupted = "{ damaged history"
        file.writeText(corrupted)
        val read = runCatching { HistoryPersistence.load() }
        val append = runCatching { panel.addEntry("new", "/responses/new.json", null) }
        assertEquals(corrupted, file.readText())
        assertTrue("Corrupt history must not look like an empty history", read.isFailure)
        assertTrue("Append must report unreadable history", append.isFailure)
    }

    private fun names(panel: HistoryTabPanel): List<String> {
        val list =
            HistoryTabPanel::class.java.getDeclaredField("list").apply { isAccessible = true }.get(panel) as JList<*>
        return (0 until list.model.size).map { (list.model.getElementAt(it) as HistoryTabPanel.HistoryEntry).scriptName }
    }

    private fun withHistory(action: () -> Unit) {
        val file = File(KttpConsolesPath.getResponsesDir(), "kttp-history.json")
        val previous = file.takeIf(File::isFile)?.readBytes()
        try {
            HistoryPersistence.clear()
            action()
        } finally {
            if (previous == null) file.delete() else file.writeBytes(previous)
        }
    }
}
