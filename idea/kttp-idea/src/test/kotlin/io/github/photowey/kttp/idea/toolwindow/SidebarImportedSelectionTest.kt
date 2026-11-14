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

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.photowey.kttp.idea.gui.GuiDraft
import io.github.photowey.kttp.idea.gui.GuiDraftService
import io.github.photowey.kttp.idea.importing.ImportCommitResult
import java.awt.Component
import java.awt.Container
import javax.swing.JTextField
import javax.swing.JLabel
import javax.swing.JComboBox
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

/**
 * Verifies a completed GUI import reveals its new draft even after filtering and collapsing the sidebar.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/14
 */
class SidebarImportedSelectionTest : BasePlatformTestCase() {
    fun testImportRevealsFirstDraftAndReplacesOldSelection() {
        val service = project.getService(GuiDraftService::class.java)
        val old = service.saveDraft(service.createEmptyDraft().copy(title = "Old draft"))
        var opened: GuiDraft? = null
        val sidebar = CollectionsSidebar(project, { _, _, _, _ -> }, { opened = it })
        val tree = descendants(sidebar).filterIsInstance<JTree>()
            .single { it.model.root.toString() == "Collections" }
        val imported = service.saveImportedDrafts((1..3).map {
            service.createEmptyDraft().copy(title = "New import $it")
        })
        try {
            PlatformTestUtil.waitWithEventsDispatching("Initial drafts loaded", {
                nodes(tree).any {
                    (it.userObject as? CollectionsSidebar.DraftNode)?.draft?.id == old.id
                }
            }, 10)
            val oldNode = nodes(tree).single { (it.userObject as? CollectionsSidebar.DraftNode)?.draft?.id == old.id }
            tree.selectionPath = TreePath(oldNode.path)
            tree.collapsePath(TreePath(oldNode.parent.let { (it as DefaultMutableTreeNode).path }))
            descendants(sidebar).filterIsInstance<JTextField>().single().text = "Old draft"
            descendants(sidebar).filterIsInstance<JComboBox<*>>().single().selectedItem =
                FileProjectionFilter.ONLY_SCRIPT
            sidebar.acceptImportedRequests(ImportCommitResult(imported, emptyList(), emptyList()))
            PlatformTestUtil.waitWithEventsDispatching("Imported draft revealed", {
                val selected = (tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode)?.userObject
                (selected as? CollectionsSidebar.DraftNode)?.draft?.id == imported.first().id
            }, 10)
            assertEquals(imported.first().id, opened?.id)
            assertEquals("", descendants(sidebar).filterIsInstance<JTextField>().single().text)
            assertEquals(
                FileProjectionFilter.ALL,
                descendants(sidebar).filterIsInstance<JComboBox<*>>().single().selectedItem
            )
            assertTrue(
                descendants(sidebar).filterIsInstance<JLabel>().any { it.text == "Imported 3 requests into Drafts" })
            assertTrue(tree.isExpanded(tree.selectionPath.parentPath))
            assertTrue(tree.isVisible(tree.selectionPath))
        } finally {
            imported.forEach { service.deleteDraft(it.id) }
            service.deleteDraft(old.id)
        }
    }

    private fun nodes(tree: JTree): List<DefaultMutableTreeNode> =
        (tree.model.root as DefaultMutableTreeNode).depthFirstEnumeration().asSequence()
            .filterIsInstance<DefaultMutableTreeNode>().toList()

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
    }
}
