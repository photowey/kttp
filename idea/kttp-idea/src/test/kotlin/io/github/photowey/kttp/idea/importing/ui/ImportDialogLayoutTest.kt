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

package io.github.photowey.kttp.idea.importing.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.JBSplitter
import io.github.photowey.kttp.idea.importing.ImportDestination
import java.awt.Component
import java.awt.Container
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.SwingUtilities

/**
 * Verifies shared import-column geometry and complete target labels when the IDE font grows.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/14
 */
class ImportDialogLayoutTest : BasePlatformTestCase() {
    fun testDestinationAndEnvironmentFollowTheSourceDivider() {
        val controls = ImportDestinationControls(project, ImportDestination.Drafts)
        controls.target.selectedIndex = 1
        val root = ImportDialogLayout().create(
            JPanel(), JTable(), JTextArea("Destination"), JButton(),
            object : AbstractAction("Refresh") {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {}
            },
            JButton(), JLabel("Ready"), JTextArea(), JTextArea(), JTextArea(),
            controls, ImportEnvironmentSelection(project)
        )
        val splitter = descendants(root).filterIsInstance<JBSplitter>().first { !it.orientation }
        for (width in listOf(800, 1060)) {
            root.setSize(width, 680)
            for (ratio in listOf(0.34f, 0.5f, 0.65f)) {
                splitter.proportion = ratio
                layoutTree(root)
                val source = surface(root, "Source")
                val destination = surface(root, "Destination")
                val environment = surface(root, "Environment")
                assertEquals(source.width, destination.width)
                assertEquals(position(root, source).x, position(root, destination).x)
                assertEquals(position(root, splitter.secondComponent).x, position(root, environment).x)
                assertEquals(splitter.secondComponent.width, environment.width)
                assertEquals(position(root, destination).y, position(root, environment).y)
            }
        }
    }

    fun testTargetLabelAndSelectionFitLargerIdeFonts() {
        val controls = ImportDestinationControls(project, ImportDestination.Drafts)
        val label = descendants(controls).filterIsInstance<JLabel>().single { it.text == "Import into" }
        for (size in listOf(17f, 20f)) {
            label.font = label.font.deriveFont(size)
            controls.target.font = label.font
            controls.setSize(370, controls.preferredSize.height)
            layoutTree(controls)
            assertTrue(
                "Import into must be shown in full",
                label.width >= label.getFontMetrics(label.font).stringWidth(label.text)
            )
            assertTrue(
                "The selected destination must fit",
                controls.target.width >= controls.target.preferredSize.width
            )
        }
    }

    private fun surface(root: JComponent, title: String): JComponent {
        val label = descendants(root).filterIsInstance<JLabel>().first { it.text == title }
        return generateSequence(label.parent as Component) { it.parent }
            .filterIsInstance<JComponent>().first { it.javaClass.simpleName == "WorkspaceSurface" }
    }

    private fun position(root: JComponent, child: Component) = SwingUtilities.convertPoint(child, 0, 0, root)

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
    }

    private fun layoutTree(component: Component) {
        if (component is Container) {
            component.doLayout()
            component.components.forEach(::layoutTree)
        }
    }
}
