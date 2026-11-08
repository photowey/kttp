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

import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.ui.TestInputDialog
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.photowey.kttp.idea.gui.GuiDraft
import io.github.photowey.kttp.idea.gui.GuiDraftService
import io.github.photowey.kttp.idea.gui.HeaderSpec
import io.github.photowey.kttp.idea.gui.ValueRef
import java.awt.Component
import java.awt.Container
import java.util.UUID
import javax.swing.JButton
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JTree
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

/**
 * Verifies draft rename, duplication, and deletion preserve editor state and selection.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class SidebarDraftMenuTest : BasePlatformTestCase() {
    fun testRenameUpdatesTheEditorBeforeTheNextSave() = withDraft { service, draft, sidebar, editor ->
        val oldInput = TestDialogManager.setTestInputDialog(TestInputDialog { "Renamed ${draft.id}" })
        try {
            clickMenu(sidebar, draft, "重命名 Draft")
            clickSave(editor)
            assertEquals("Renamed ${draft.id}", service.findDraft(draft.id)!!.title)
            waitForDraft(sidebar, draft.id) { it.title == "Renamed ${draft.id}" }
        } finally {
            TestDialogManager.setTestInputDialog(oldInput)
        }
    }

    fun testRenamePreservesUnsavedUrlAndHeaderThroughTheNextSave() = withDraft { service, draft, sidebar, editor ->
        descendants(editor).filterIsInstance<ComposerHeaderPanel>().single().urlField.text = "http://localhost/unsaved"
        descendants(editor).filterIsInstance<HeadersTabPanel>().single()
            .setHeaderEntries(listOf("X-Edited" to "unsaved"))
        val oldInput = TestDialogManager.setTestInputDialog(TestInputDialog { "Renamed dirty draft" })
        try {
            clickMenu(sidebar, draft, "重命名 Draft")
            clickSave(editor)
            val saved = service.findDraft(draft.id)!!
            assertEquals("Renamed dirty draft", saved.title)
            assertEquals(ValueRef.Literal("http://localhost/unsaved"), saved.request.target.url)
            assertEquals(listOf(HeaderSpec("X-Edited", ValueRef.Literal("unsaved"))), saved.request.headers)
        } finally {
            TestDialogManager.setTestInputDialog(oldInput)
        }
    }

    fun testRenameCommitsTheActiveCellOfACleanDraftBeforeReloadingIt() = withDraft { service, draft, sidebar, editor ->
        val headers = descendants(editor).filterIsInstance<HeadersTabPanel>().single()
        headers.setHeaderEntries(listOf("X-Pending" to "saved value"))
        clickSave(editor)
        val table = descendants(headers).filterIsInstance<JTable>().single()
        assertTrue(table.editCellAt(0, 1))
        (table.editorComponent as JTextField).text = "typed before rename"
        assertEquals("The test must leave the edit outside the table model", "saved value", table.getValueAt(0, 1))
        val oldInput = TestDialogManager.setTestInputDialog(TestInputDialog { "Renamed with pending cell" })
        try {
            clickMenu(sidebar, draft, "重命名 Draft")
            clickSave(editor)
            val saved = service.findDraft(draft.id)!!
            assertEquals("Renamed with pending cell", saved.title)
            assertEquals(
                listOf(HeaderSpec("X-Pending", ValueRef.Literal("typed before rename"))),
                saved.request.headers
            )
        } finally {
            TestDialogManager.setTestInputDialog(oldInput)
        }
    }

    fun testDuplicateKeepsTheNewCopySelectedAfterAsyncRefresh() = withDraft { service, draft, sidebar, editor ->
        clickMenu(sidebar, draft, "复制 Draft")
        val duplicate = service.listDrafts().single { it.id != draft.id && it.title == "Copy of ${draft.title}" }
        waitForDraft(sidebar, duplicate.id)
        val selected = (requestTree(sidebar).selectionPath?.lastPathComponent as DefaultMutableTreeNode).userObject
            as CollectionsSidebar.DraftNode
        assertEquals(duplicate.id, selected.draft.id)
        clickSave(editor)
        assertEquals(draft.title, service.findDraft(draft.id)!!.title)
        assertEquals(duplicate.title, service.findDraft(duplicate.id)!!.title)
    }

    fun testDeleteStartsANewDraftSoSaveCannotResurrectTheDeletedOne() = withDraft { service, draft, sidebar, editor ->
        val oldDialog = TestDialogManager.setTestDialog(TestDialog { Messages.YES })
        try {
            clickMenu(sidebar, draft, "删除 Draft")
            PlatformTestUtil.waitWithEventsDispatching(
                "deleted draft disappears",
                { findDraft(sidebar, draft.id) == null },
                10
            )
            clickSave(editor)
            assertNull("Saving the new editor must not recreate the deleted ID", service.findDraft(draft.id))
        } finally {
            TestDialogManager.setTestDialog(oldDialog)
        }
    }

    private fun withDraft(action: (GuiDraftService, GuiDraft, CollectionsSidebar, RequestEditorPanel) -> Unit) {
        val service = project.getService(GuiDraftService::class.java)
        val previousIds = service.listDrafts().map { it.id }.toSet()
        val draft = service.saveDraft(service.createEmptyDraft().copy(title = "Menu test ${UUID.randomUUID()}"))
        val editor = RequestEditorPanel(project) { _, _ -> }
        val sidebar = CollectionsSidebar(
            project,
            onRequestSelected = { declaration, file, index, focus ->
                editor.loadFromRequest(
                    declaration,
                    file,
                    index,
                    focus
                )
            },
            onDraftSelected = editor::loadDraft
        )
        try {
            waitForDraft(sidebar, draft.id)
            requestTree(sidebar).selectionPath = TreePath(findDraft(sidebar, draft.id)!!.path)
            action(service, draft, sidebar, editor)
        } finally {
            service.listDrafts().filter { it.id !in previousIds }.forEach { service.deleteDraft(it.id) }
        }
    }

    private fun clickMenu(sidebar: CollectionsSidebar, draft: GuiDraft, text: String) {
        val menu = JPopupMenu()
        sidebar.addDraftContextMenuItems(menu, draft)
        menu.components.filterIsInstance<JMenuItem>().single { it.text == text }.doClick()
    }

    private fun clickSave(editor: RequestEditorPanel) {
        descendants(editor).filterIsInstance<JButton>().single { it.text == "Save Draft" }.doClick()
    }

    private fun waitForDraft(sidebar: CollectionsSidebar, id: String, predicate: (GuiDraft) -> Boolean = { true }) {
        PlatformTestUtil.waitWithEventsDispatching("draft tree refresh", {
            val draft = findDraft(sidebar, id)?.userObject as? CollectionsSidebar.DraftNode
            draft != null && predicate(draft.draft)
        }, 10)
    }

    private fun findDraft(sidebar: CollectionsSidebar, id: String): DefaultMutableTreeNode? {
        val root = requestTree(sidebar).model.root as DefaultMutableTreeNode
        return root.depthFirstEnumeration().asSequence().filterIsInstance<DefaultMutableTreeNode>().firstOrNull {
            (it.userObject as? CollectionsSidebar.DraftNode)?.draft?.id == id
        }
    }

    private fun requestTree(sidebar: CollectionsSidebar): JTree =
        descendants(sidebar).filterIsInstance<JTree>().single { it.model.root.toString() == "Collections" }

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
    }
}
