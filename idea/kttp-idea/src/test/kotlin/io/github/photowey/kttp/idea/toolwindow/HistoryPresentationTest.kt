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

import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.LightPlatformTestCase
import io.github.photowey.kttp.idea.io.HistoryPersistence
import java.nio.file.Path
import javax.swing.JLabel
import javax.swing.JList

/**
 * Verifies shortened history paths and full-path tooltips across project, console, and external files.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class HistoryPresentationTest : LightPlatformTestCase() {
    fun testConsoleResponseUsesShortLabelAndFullPathTooltip() {
        val panel = HistoryTabPanel(project)
        val list = historyList(panel)
        val path = Path.of(PathManager.getConfigPath(), "consoles", "Kttp", "responses", "sample.json").toString()
        val label = render(list, HistoryTabPanel.HistoryEntry("POST sample", path))

        assertEquals("POST sample → responses/sample.json", label.text)
        assertEquals(path, label.toolTipText)
    }

    fun testRendererClearsThePreviousTooltipWhenReusedForAnotherValue() {
        val list = historyList(HistoryTabPanel(project))
        val fullPath = "/outside/sample.json"
        assertEquals(fullPath, render(list, HistoryTabPanel.HistoryEntry("request", fullPath)).toolTipText)
        assertEquals("placeholder", render(list, "placeholder").text)
        assertNull(render(list, "placeholder").toolTipText)
        assertEquals("", render(list, null).text)
        assertNull(render(list, null).toolTipText)
    }

    fun testProjectResponseIsRelativeToTheProjectRoot() {
        val projectRoot = Path.of(System.getProperty("java.io.tmpdir"), "history-project")
        val response = projectRoot.resolve(".kttp/responses/project.json")
        assertEquals(
            ".kttp/responses/project.json",
            historyResponseDisplayPath(response.toString(), projectRoot.toString(), "/console-root")
        )
    }

    fun testConsoleRootTakesPriorityWhenItIsInsideTheProject() {
        val projectRoot = Path.of(System.getProperty("java.io.tmpdir"), "history-project")
        val consoleRoot = projectRoot.resolve("build/idea/config/consoles/Kttp")
        assertEquals(
            "responses/console.json", historyResponseDisplayPath(
                consoleRoot.resolve("responses/console.json").toString(), projectRoot.toString(), consoleRoot.toString()
            )
        )
    }

    fun testExternalPathsAndSimilarDirectoryPrefixesUseOnlyTheFileName() {
        val root = Path.of(System.getProperty("java.io.tmpdir"), "history-project")
        val consoleRoot = root.resolve("console/Kttp")
        for (outside in listOf(
            root.resolveSibling("history-project-copy/response.json"),
            consoleRoot.resolveSibling("Kttp-copy/response.json")
        )) {
            assertEquals("response.json", historyResponseDisplayPath(outside.toString(), null, consoleRoot.toString()))
        }
        assertEquals(
            "response.json", historyResponseDisplayPath(
                root.resolveSibling("history-project-copy/response.json").toString(),
                root.toString(),
                consoleRoot.toString()
            )
        )
    }

    fun testLexicalNormalizationKeepsNestedPathsButRejectsEscapingTheRoot() {
        val root = Path.of(System.getProperty("java.io.tmpdir"), "history-project")
        val consoleRoot = root.resolve("console/Kttp")
        assertEquals(
            "responses/normalized.json", historyResponseDisplayPath(
                consoleRoot.resolve("responses/old/../normalized.json").toString(),
                null,
                consoleRoot.resolve(".").toString()
            )
        )
        assertEquals(
            "escaped.json", historyResponseDisplayPath(
                consoleRoot.resolve("../escaped.json").toString(), null, consoleRoot.toString()
            )
        )
    }

    fun testDisplayDoesNotReplaceStoredOrSelectedAbsolutePaths() {
        val previous = HistoryPersistence.load()
        try {
            val panel = HistoryTabPanel(project)
            val responsePath =
                Path.of(PathManager.getConfigPath(), "consoles", "Kttp", "responses", "stored.json").toString()
            val scriptPath = Path.of(PathManager.getConfigPath(), "consoles", "Kttp", "stored.kttp.kts").toString()
            panel.addEntry("stored", responsePath, scriptPath)
            val list = historyList(panel)
            list.selectedIndex = list.model.size - 1
            render(list, list.selectedValue)

            assertEquals(responsePath, panel.getSelectedResponseFilePath())
            assertEquals(scriptPath, panel.getSelectedScriptFilePath())
            val record = HistoryPersistence.load().last()
            assertEquals(responsePath, record.responseFilePath)
            assertEquals(scriptPath, record.scriptFilePath)
        } finally {
            HistoryPersistence.clear()
            HistoryPersistence.append(previous)
        }
    }

    fun testClearShowsTheEmptyStateAndAllowsAddingANewEntry() {
        val previous = HistoryPersistence.load()
        try {
            val panel = HistoryTabPanel(project)
            panel.addEntry("first", "/responses/first.json", null)
            panel.clear()

            assertEquals(1, panel.componentCount)
            assertTrue(panel.getComponent(0) is JLabel)
            assertEquals("No history.", (panel.getComponent(0) as JLabel).text)
            assertTrue(HistoryPersistence.load().isEmpty())

            panel.addEntry("next", "/responses/next.json", null)
            val list = historyList(panel)
            assertEquals(1, list.model.size)
            list.selectedIndex = 0
            assertEquals("/responses/next.json", panel.getSelectedResponseFilePath())
            assertFalse(panel.getComponent(0) is JLabel)
        } finally {
            HistoryPersistence.clear()
            HistoryPersistence.append(previous)
        }
    }

    fun testReloadAfterHistoryBecomesEmptyShowsTheEmptyState() {
        val previous = HistoryPersistence.load()
        try {
            val panel = HistoryTabPanel(project)
            panel.addEntry("first", "/responses/first.json", null)
            HistoryPersistence.clear()

            panel.reload()

            assertEquals(1, panel.componentCount)
            assertTrue(panel.getComponent(0) is JLabel)
            assertEquals("No history yet.", (panel.getComponent(0) as JLabel).text)
            assertEquals(0, historyList(panel).model.size)
            panel.reload()
            assertEquals(1, panel.componentCount)
        } finally {
            HistoryPersistence.clear()
            HistoryPersistence.append(previous)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun render(list: JList<*>, entry: Any?): JLabel =
        (list.cellRenderer as javax.swing.ListCellRenderer<Any?>)
            .getListCellRendererComponent(list as JList<Any?>, entry, 0, false, false) as JLabel

    private fun historyList(panel: HistoryTabPanel): JList<*> =
        HistoryTabPanel::class.java.getDeclaredField("list").apply { isAccessible = true }.get(panel) as JList<*>
}
