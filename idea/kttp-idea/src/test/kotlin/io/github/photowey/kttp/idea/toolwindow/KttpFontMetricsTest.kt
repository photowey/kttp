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
import io.github.photowey.kttp.idea.gui.DraftOwnership
import io.github.photowey.kttp.idea.gui.GuiMode
import io.github.photowey.kttp.idea.script.KttpRequestDeclaration
import io.github.photowey.kttp.idea.viewer.ResponseViewer
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.awt.Component
import java.awt.Container
import java.awt.BorderLayout
import java.awt.Font
import java.awt.Rectangle
import javax.swing.JLabel
import javax.swing.JComboBox
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JTree
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.plaf.FontUIResource
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Verifies that composer controls and request rows accommodate UI font changes and narrow widths.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class KttpFontMetricsTest : LightPlatformTestCase() {
    fun testComposerInputsAccommodateLargeUiFontsAtConstruction() = withUiFonts {
        setUiFont(28)
        val header = ComposerHeaderPanel()
        listOf(header.titleField, header.requestNameField, header.urlField, header.methodComboBox).forEach { input ->
            val textHeight = input.getFontMetrics(input.font).height
            assertTrue(
                "${input.javaClass.simpleName}: ${input.preferredSize.height} must fit text height $textHeight",
                input.preferredSize.height >= textHeight + input.insets.top + input.insets.bottom
            )
        }
    }

    fun testComposerControlsFitWithinRowsAtLargeFontsAndNarrowHostWidths() = withUiFonts {
        setUiFont(28)
        val header = ComposerHeaderPanel().apply {
            updateModeBadge(DraftOwnership.DETACHED, GuiMode.GUI_READY, false)
            requestProgress.start(io.github.photowey.kttp.idea.execution.RequestExecutionMode.FOLLOW_SCRIPT)
        }
        val host = JPanel(BorderLayout()).apply { add(header, BorderLayout.NORTH) }
        try {
            for (width in listOf(800, 600)) {
                host.setSize(width, 1000)
                layout(host)
                descendants(header).filter {
                    it is JLabel || it is JTextField || it is javax.swing.AbstractButton || it is JComboBox<*>
                }.forEach { control ->
                    val bounds = Rectangle(0, 0, control.parent.width, control.parent.height)
                    assertTrue(
                        "${control.javaClass.simpleName} ${control.bounds} outside row $bounds at width $width",
                        bounds.contains(control.bounds)
                    )
                }
            }
        } finally {
            header.requestProgress.dispose()
        }
    }

    fun testComposerFontChangesDoNotRestoreSmallBadgeOrBridgeFonts() = withUiFonts {
        setUiFont(12)
        val header = ComposerHeaderPanel()
        val bridge = ComposerBridgePanel()
        setUiFont(28)
        SwingUtilities.updateComponentTreeUI(header)
        SwingUtilities.updateComponentTreeUI(bridge)
        header.updateModeBadge(DraftOwnership.DETACHED, GuiMode.GUI_READY, false)
        descendants(header).filterIsInstance<JLabel>().forEach { label ->
            assertEquals("Composer label ${label.text}", 28, label.font.size)
        }
        descendants(bridge).filterIsInstance<JLabel>().forEach { label ->
            assertEquals("Bridge label ${label.text}", 28, label.font.size)
        }
        assertTextAreaFonts(bridge, 28)
        bridge.components.filterIsInstance<Container>().forEach { section ->
            assertTrue(
                "Bridge sections must accommodate their font-dependent preferred height",
                section.maximumSize.height >= section.preferredSize.height
            )
        }
    }

    fun testRequestTableRowsFitFontsInitiallyAndAfterUiAndFontChanges() = withUiFonts {
        setUiFont(28)
        val panels = listOf(ParamsTabPanel(), HeadersTabPanel(), BodyTabPanel())
        val tables = panels.flatMap { descendants(it).filterIsInstance<JTable>().toList() }
        tables.forEach(::assertTableTextFits)
        setUiFont(32)
        panels.forEach(SwingUtilities::updateComponentTreeUI)
        tables.forEach { table ->
            assertEquals(32, table.font.size)
            assertTableTextFits(table)
            table.font = table.font.deriveFont(36f)
            assertTableTextFits(table)
        }
    }

    fun testBodyBridgeAndResponseTextFollowUiFontChanges() = withUiFonts {
        setUiFont(28)
        val panels = listOf(BodyTabPanel(), ComposerBridgePanel(), AssertionsTabPanel(project), ResponseViewer(project))
        panels.forEach { assertTextAreaFonts(it, 28) }
        setUiFont(32)
        panels.forEach { panel ->
            SwingUtilities.updateComponentTreeUI(panel)
            assertTextAreaFonts(panel, 32)
        }
    }

    fun testAuthInputsFitLargeFontsAfterThemeRefresh() = withUiFonts {
        setUiFont(12)
        val panel = AuthTabPanel()
        setUiFont(28)
        SwingUtilities.updateComponentTreeUI(panel)
        descendants(panel).filterIsInstance<javax.swing.JTextField>().forEach { field ->
            assertEquals(28, field.font.size)
            assertTrue(field.preferredSize.height >= field.getFontMetrics(field.font).height + field.insets.top + field.insets.bottom)
        }
    }

    fun testHistoryAndResponseRowsKeepLargeFontsAfterThemeRefresh() = withUiFonts {
        setUiFont(12)
        val history = HistoryTabPanel(project).apply { showEmpty("No history.") }
        val responses = SidebarResponses(project, SidebarFileDiscovery(project)).component
        val tree = descendants(responses).filterIsInstance<JTree>().single()
        val model = tree.model as DefaultTreeModel
        (model.root as DefaultMutableTreeNode).add(DefaultMutableTreeNode("saved-response.json"))
        model.reload()
        setUiFont(28)
        SwingUtilities.updateComponentTreeUI(history)
        SwingUtilities.updateComponentTreeUI(responses)
        assertEquals(28, (history.getComponent(0) as JLabel).font.size)
        descendants(responses).filterIsInstance<JLabel>().forEach { label -> assertEquals(28, label.font.size) }
        val row = tree.getPathForRow(0).lastPathComponent
        val rendered = tree.cellRenderer.getTreeCellRendererComponent(tree, row, false, false, true, 0, false)
        assertEquals("Response renderer must use the refreshed tree font", 28, rendered.font.size)
        assertTrue(
            "Response rows must fit their renderer",
            tree.getRowBounds(0).height >= rendered.preferredSize.height
        )
    }

    fun testResponseEmptyStateFollowsLargeUiFontChanges() = withUiFonts {
        setUiFont(28)
        val panel = ResponseTabPanel(project).apply { showEmpty("Waiting for a response") }
        val empty = descendants(panel).filterIsInstance<JLabel>().single { it.text == "Waiting for a response" }
        assertEquals(28, empty.font.size)
        setUiFont(32)
        SwingUtilities.updateComponentTreeUI(panel)
        assertEquals(32, empty.font.size)
    }

    fun testCollectionsEmptyAndHeaderLabelsUseLargeUiFontInitiallyAndAfterRefresh() = withUiFonts {
        setUiFont(28)
        val sidebar = CollectionsSidebar(project, { _, _, _, _ -> }, {})
        val labels =
            descendants(sidebar).filterIsInstance<JLabel>().filter { it.text in listOf("Loading...", "Sources") }
                .toList()
        assertEquals(2, labels.size)
        labels.forEach { assertEquals(it.text, 28, it.font.size) }
        setUiFont(32)
        SwingUtilities.updateComponentTreeUI(sidebar)
        labels.forEach { assertEquals(it.text, 32, it.font.size) }
    }

    fun testCollectionsSearchAndFilterFitAfterUiFontChanges() = withUiFonts {
        for (initialSize in listOf(12, 28)) {
            setUiFont(initialSize)
            val sidebar = CollectionsSidebar(project, { _, _, _, _ -> }, {})
            setUiFont(28)
            SwingUtilities.updateComponentTreeUI(sidebar)
            val search = descendants(sidebar).filterIsInstance<SidebarSearchControls>().single()
            val filter = descendants(sidebar).filterIsInstance<JComboBox<*>>().single()
            for (control in listOf(search, filter)) {
                assertTrue(
                    "${control.javaClass.simpleName} maximum height must follow its current preferred size",
                    control.maximumSize.height >= control.preferredSize.height
                )
            }
        }
    }

    fun testSourceTreeRowsAndCachedRendererFollowTheCurrentTreeFont() = withUiFonts {
        setUiFont(28)
        val sidebar = CollectionsSidebar(project, { _, _, _, _ -> }, {})
        val tree = descendants(sidebar).filterIsInstance<JTree>().single { it.model.root.toString() == "Collections" }
        val model = tree.model as DefaultTreeModel
        val root = model.root as DefaultMutableTreeNode
        root.removeAllChildren()
        val call = KtPsiFactory(project).createExpression("GET(\"https://example.com/font\")") as KtCallExpression
        root.add(DefaultMutableTreeNode(CollectionsSidebar.GroupNode.PROJECT))
        root.add(
            DefaultMutableTreeNode(
                CollectionsSidebar.RequestNode(
                    KttpRequestDeclaration(call), "GET",
                    "https://example.com/font", GuiMode.GUI_READY, "Font test"
                )
            )
        )
        model.reload()
        assertSourceRowsFit(tree, 28)
        setUiFont(32)
        SwingUtilities.updateComponentTreeUI(sidebar)
        assertSourceRowsFit(tree, 32)
    }

    private fun assertSourceRowsFit(tree: JTree, size: Int) {
        for (row in 0 until tree.rowCount) {
            val node = tree.getPathForRow(row).lastPathComponent
            val rendered = tree.cellRenderer.getTreeCellRendererComponent(tree, node, false, false, true, row, false)
            val labels = descendants(rendered).filterIsInstance<JLabel>().toList()
            assertTrue("Source text must follow the current tree font", labels.any { it.font.size == size })
            labels.forEach { assertTrue("Badges must derive from the current tree font", it.font.size >= size - 3) }
            assertTrue(
                "Source row must fit the rendered controls",
                tree.getRowBounds(row).height >= rendered.preferredSize.height
            )
        }
    }

    private fun assertTextAreaFonts(panel: Component, size: Int) {
        val areas = descendants(panel).filterIsInstance<JTextArea>().toList()
        assertFalse("Expected real text controls", areas.isEmpty())
        areas.forEach { area -> assertEquals("${panel.javaClass.simpleName} text font", size, area.font.size) }
    }

    private fun assertTableTextFits(table: JTable) {
        assertTrue(
            "Row height ${table.rowHeight} must fit font ${table.font.size} with cell padding",
            table.rowHeight > table.getFontMetrics(table.font).height
        )
    }

    private fun withUiFonts(action: () -> Unit) {
        val originals = FONT_KEYS.associateWith(UIManager::get)
        try {
            action()
        } finally {
            originals.forEach { (key, value) -> UIManager.put(key, value) }
        }
    }

    private fun setUiFont(size: Int) {
        FONT_KEYS.forEach { UIManager.put(it, FontUIResource(Font(Font.DIALOG, Font.PLAIN, size))) }
    }

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
    }

    private fun layout(container: Container) {
        container.doLayout()
        container.components.filterIsInstance<Container>().forEach(::layout)
    }

    /**
     * Lists the UI font defaults adjusted by font-metrics tests.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    private companion object {
        val FONT_KEYS = listOf(
            "Label.font", "TextField.font", "PasswordField.font", "TextArea.font", "Table.font",
            "TableHeader.font", "ComboBox.font", "Button.font", "CheckBox.font", "List.font", "Tree.font"
        )
    }
}
