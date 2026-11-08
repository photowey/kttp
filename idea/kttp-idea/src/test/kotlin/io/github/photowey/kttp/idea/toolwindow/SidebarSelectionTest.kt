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

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

/**
 * Verifies refresh and reparsing preserve request selection and tree expansion state.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class SidebarSelectionTest : BasePlatformTestCase() {
    fun testRefreshPreservesSelectedRequestWithoutReopeningTheEditor() {
        val file = myFixture.addFileToProject(
            "selected.kttp.kts",
            "GET(\"https://example.com/first\")\nPOST(\"https://example.com/second\")"
        ).virtualFile
        val callbacks = mutableListOf<Pair<String?, Int?>>()
        val sidebar = CollectionsSidebar(
            project,
            onRequestSelected = { _, source, index, _ -> callbacks += source?.path to index },
            onDraftSelected = {})
        val tree = requestTree(sidebar)
        val fileNode = waitForFile(tree, file.path)
        tree.expandPath(TreePath(fileNode.path))
        waitFor("request expansion") { fileNode.childCount == 2 && fileNode.firstChild.let { it as DefaultMutableTreeNode }.userObject is CollectionsSidebar.RequestNode }
        tree.selectionPath = TreePath((fileNode.getChildAt(1) as DefaultMutableTreeNode).path)
        assertEquals(listOf(file.path to 1), callbacks)

        refreshAndWait(sidebar, tree)

        val selected = tree.selectionPath.lastPathComponent as DefaultMutableTreeNode
        assertEquals("https://example.com/second", (selected.userObject as CollectionsSidebar.RequestNode).displayUrl)
        assertEquals(listOf(file.path to 1), callbacks)
        assertEquals(
            file.path,
            ((selected.parent as DefaultMutableTreeNode).userObject as CollectionsSidebar.FileNode).file.path
        )
    }

    fun testScriptReparsePreservesRequestSelectionWithoutReopeningTheEditor() {
        val file = myFixture.addFileToProject("edited.kttp.kts", "GET(\"https://example.com/before\")").virtualFile
        var callbacks = 0
        val sidebar = CollectionsSidebar(project, { _, _, _, _ -> callbacks++ }, {})
        val tree = requestTree(sidebar)
        val node = waitForFile(tree, file.path)
        tree.expandPath(TreePath(node.path))
        waitFor("initial request") { (node.firstChild as DefaultMutableTreeNode).userObject is CollectionsSidebar.RequestNode }
        tree.selectionPath = TreePath((node.firstChild as DefaultMutableTreeNode).path)
        runWriteAction {
            val document = requireNotNull(FileDocumentManager.getInstance().getDocument(file))
            document.setText("POST(\"https://example.com/after\")")
            PsiDocumentManager.getInstance(project).commitDocument(document)
            FileDocumentManager.getInstance().saveDocument(document)
        }

        waitFor("edited selected request") {
            val selected =
                (tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? CollectionsSidebar.RequestNode
            selected?.displayMethod == "POST" && selected.displayUrl == "https://example.com/after"
        }
        assertEquals(1, callbacks)
        assertTrue(tree.isExpanded(TreePath(waitForFile(tree, file.path).path)))
    }

    fun testRefreshPreservesExpandedFileAndCollapsedGroup() {
        val file = myFixture.addFileToProject("expanded.kttp.kts", "GET(\"https://example.com/expanded\")").virtualFile
        val sidebar = CollectionsSidebar(project, { _, _, _, _ -> }, {})
        val tree = requestTree(sidebar)
        tree.expandPath(TreePath(waitForFile(tree, file.path).path))
        val kttpGroup = group(tree, "Kttp")
        tree.collapsePath(TreePath(kttpGroup.path))

        refreshAndWait(sidebar, tree)

        assertTrue(tree.isExpanded(TreePath(waitForFile(tree, file.path).path)))
        assertFalse(tree.isExpanded(TreePath(group(tree, "Kttp").path)))
    }

    fun testAddRequestPendingSelectionWinsOverTheCurrentFile() {
        val oldFile = myFixture.addFileToProject("old.kttp.kts", "GET(\"https://example.com/old\")").virtualFile
        var addedFile: com.intellij.openapi.vfs.VirtualFile? = null
        val sidebar = CollectionsSidebar(project, { _, _, _, _ -> }, {}, onAddRequestToScript = { addedFile })
        val tree = requestTree(sidebar)
        tree.selectionPath = TreePath(waitForFile(tree, oldFile.path).path)
        addedFile = myFixture.addFileToProject("new.kttp.kts", "POST(\"https://example.com/new\")").virtualFile

        descendants(sidebar).filterIsInstance<JButton>().single { it.text == "Add Request" }.doClick()

        waitFor("new script selection") {
            val selected = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
            (selected?.userObject as? CollectionsSidebar.FileNode)?.file == addedFile
        }
    }

    private fun refreshAndWait(sidebar: CollectionsSidebar, tree: JTree) {
        val root = tree.model.root as DefaultMutableTreeNode
        val previousGroup = root.firstChild
        sidebar.refreshCollections()
        waitFor("collection refresh") { root.childCount > 0 && root.firstChild !== previousGroup }
    }

    private fun waitForFile(tree: JTree, path: String): DefaultMutableTreeNode {
        waitFor("file appears") { findFile(tree, path) != null }
        return requireNotNull(findFile(tree, path))
    }

    private fun findFile(tree: JTree, path: String): DefaultMutableTreeNode? =
        (tree.model.root as DefaultMutableTreeNode).depthFirstEnumeration().asSequence()
            .filterIsInstance<DefaultMutableTreeNode>()
            .firstOrNull { (it.userObject as? CollectionsSidebar.FileNode)?.file?.path == path }

    private fun group(tree: JTree, name: String): DefaultMutableTreeNode =
        (tree.model.root as DefaultMutableTreeNode).children().asSequence()
            .filterIsInstance<DefaultMutableTreeNode>().single { it.userObject.toString() == name }

    private fun waitFor(description: String, condition: () -> Boolean) {
        PlatformTestUtil.waitWithEventsDispatching(description, condition, 10)
    }

    private fun requestTree(sidebar: CollectionsSidebar): JTree =
        descendants(sidebar).filterIsInstance<JTree>().single { it.model.root.toString() == "Collections" }

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
    }
}
