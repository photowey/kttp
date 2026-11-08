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

import com.intellij.openapi.ui.Splitter
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JViewport

/**
 * Verifies request and response splitter expansion and sidebar toolbar layout.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class KttpEditorLayoutTest : BasePlatformTestCase() {
    fun testRequestAndResponseCanBothBeExpanded() {
        val panel = KttpToolWindowPanel(project)
        panel.setSize(1400, 920)
        layout(panel)
        val results = panel.responseTabPanel.parent.parent
        val split = generateSequence(results as Component?) { it.parent }
            .filterIsInstance<Splitter>().firstOrNull()
        assertNotNull("Request and results need a draggable vertical splitter", split)
        split!!
        assertTrue(split.isVertical)
        split.proportion = 0.75f
        layout(panel)
        val smallerResponse = panel.responseTabPanel.height
        split.proportion = 0.35f
        layout(panel)
        assertTrue(
            "Dragging up must give the response more space",
            panel.responseTabPanel.height > smallerResponse + 150
        )
        val editor = descendants(panel).filterIsInstance<RequestEditorPanel>().single()
        assertFalse("The entire editor must not sit in a vertically scrolling viewport", editor.parent is JViewport)
    }

    fun testSidebarActionsFitInsideTheirToolbar() {
        val panel = KttpToolWindowPanel(project)
        panel.setSize(1400, 920)
        layout(panel)
        val sidebar = descendants(panel).filterIsInstance<CollectionsSidebar>().single()
        val actions = descendants(sidebar).filterIsInstance<JButton>()
            .filter { it.text in listOf("New Draft", "Add Request") || it.toolTipText == "Refresh drafts and scripts" }
            .toList()
        assertEquals(3, actions.size)
        actions.forEach { button ->
            assertTrue("${button.text} is clipped outside its toolbar", button.parent.bounds.let {
                java.awt.Rectangle(0, 0, it.width, it.height).contains(button.bounds)
            })
        }
    }

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
    }

    private fun layout(container: Container) {
        container.doLayout()
        container.components.filterIsInstance<Container>().forEach(::layout)
    }
}
