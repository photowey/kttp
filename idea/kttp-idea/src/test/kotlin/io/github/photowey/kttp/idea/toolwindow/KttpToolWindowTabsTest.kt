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

import java.awt.Component
import javax.swing.JPanel
import javax.swing.JTabbedPane
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Verifies result-tab order and preservation of the supplied tab components.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class KttpToolWindowTabsTest {

    @Test
    fun installResultTabsUsesStableTabOrder() {
        val tabs = JTabbedPane()

        KttpToolWindowPanel.installResultTabs(
            tabbedPane = tabs,
            responseTabPanel = JPanel(),
            historyTabPanel = JPanel(),
            assertionsTabPanel = JPanel()
        )

        assertEquals(
            listOf("Response", "History", "Assertions"),
            (0 until tabs.tabCount).map(tabs::getTitleAt)
        )
    }

    @Test
    fun installResultTabsPreservesProvidedComponents() {
        val tabs = JTabbedPane()
        val response = JPanel()
        val history = JPanel()
        val assertions = JPanel()

        KttpToolWindowPanel.installResultTabs(tabs, response, history, assertions)

        assertEquals(
            listOf<Component>(response, history, assertions),
            (0 until tabs.tabCount).map(tabs::getComponentAt)
        )
    }
}
