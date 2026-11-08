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

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.JBSplitter
import java.awt.Component
import java.awt.Container
import javax.swing.JTabbedPane

/**
 * Verifies adjustable workspace regions remain usable with oversized editor content.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/07
 */
class WorkspaceLayoutTest : BasePlatformTestCase() {
    fun testEveryWorkspaceBoundaryCanResizeWithoutHonoringEditorMinimums() {
        val panel = KttpToolWindowPanel(project)
        panel.setSize(1280, 900)
        layoutTree(panel)
        val splitters = descendants(panel).filterIsInstance<JBSplitter>().toList()
        assertEquals(4, splitters.size)
        assertEquals(4, splitters.map { it.splitterProportionKey }.toSet().size)
        splitters.forEach { split ->
            assertFalse(split.isHonorMinimumSize)
            assertTrue(split.dividerWidth >= 7)
            split.firstComponent.minimumSize = java.awt.Dimension(5000, 5000)
            split.secondComponent.minimumSize = java.awt.Dimension(5000, 5000)
            split.proportion = 0.3f
            split.doLayout()
            val firstSize = if (split.isVertical) split.firstComponent.height else split.firstComponent.width
            val total = (if (split.isVertical) split.height else split.width) - split.dividerWidth
            assertTrue("Divider should follow requested proportion", kotlin.math.abs(firstSize - total * 0.3) <= 2)
            split.proportion = 0.7f
            split.doLayout()
            val largerSize = if (split.isVertical) split.firstComponent.height else split.firstComponent.width
            assertTrue("Both sides must be resizable", largerSize > firstSize)
        }
    }

    fun testHeaderKeepsPrimaryControlsVisibleWithALongUrl() {
        val header = ComposerHeaderPanel()
        header.urlField.text = "https://example.com/" + "very-long-path/".repeat(80)
        val host = javax.swing.JPanel(java.awt.BorderLayout()).apply { add(header, java.awt.BorderLayout.NORTH) }
        host.setSize(700, 600)
        layoutTree(host)
        assertTrue("Header should leave room for payload editing", header.height < 240)
        assertTrue(header.urlField.width > 150)
        assertTrue(header.sendButton.bounds.width > 0)
        assertTrue(header.sendButton.locationOnPanel(header).x + header.sendButton.width <= header.width)
    }

    fun testRequestMethodAlignsWithTheExecutionModeControl() {
        val header = ComposerHeaderPanel()
        val host = javax.swing.JPanel(java.awt.BorderLayout()).apply { add(header, java.awt.BorderLayout.NORTH) }
        for (width in listOf(600, 900, 1200)) {
            host.setSize(width, 600)
            layoutTree(host)
            assertEquals(
                "Request and action rows should share a left edge at width $width",
                header.executionModeCombo.locationOnPanel(header).x,
                header.methodComboBox.locationOnPanel(header).x
            )
        }
    }

    fun testScriptInformationUsesTabsInsteadOfStackedScrollPanes() {
        val bridge = ComposerBridgePanel()
        val tabs = descendants(bridge).filterIsInstance<JTabbedPane>().single()
        assertEquals(listOf("Script", "Details"), (0 until tabs.tabCount).map(tabs::getTitleAt))
    }

    fun testLinkedScriptPreviewIsClearlyIdentifiedAsGeneratedHttpData() {
        val bridge = ComposerBridgePanel()
        bridge.update("", "", "", "", "/project/requests/sample.kttp.kts", "GET(\"https://example.com\") {}")
        val label = descendants(bridge).filterIsInstance<javax.swing.JLabel>()
            .single { it.text.contains("sample.kttp.kts") }
        assertTrue(label.text.contains("Preview"))
        assertTrue(label.toolTipText.contains("Generated HTTP projection"))
        assertTrue(label.toolTipText.contains("complete Kotlin source"))
    }

    private fun Component.locationOnPanel(panel: Component) = javax.swing.SwingUtilities.convertPoint(parent, location, panel)

    private fun layoutTree(component: Component) {
        if (component is Container) {
            component.doLayout()
            component.components.forEach(::layoutTree)
        }
    }

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
    }
}
