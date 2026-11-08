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
import io.github.photowey.kttp.idea.gui.GuiDraftService
import io.github.photowey.kttp.idea.gui.GuiMode
import java.awt.Component
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import java.util.UUID

/**
 * Exercise the actual controls, debounce timer, PSI projection and published tree.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class SidebarFilteringTest : BasePlatformTestCase() {
    fun testTreeShowsUniformCountsAndCapabilityLabelsWithExecutionGuidance() {
        addScripts()
        val sidebar = CollectionsSidebar(project, { _, _, _, _ -> }, {})
        val tree = requestTree(sidebar)
        await("initial script scan") { scriptNames(tree).size == 4 }
        for (fileNode in projectFiles(tree)) {
            tree.expandPath(TreePath(fileNode.path))
            await("requests parsed for ${fileNode.userObject}") {
                fileNode.children().asSequence().any {
                    (it as DefaultMutableTreeNode).userObject is CollectionsSidebar.RequestNode
                }
            }
            val requests = fileNode.children().asSequence().filterIsInstance<DefaultMutableTreeNode>().toList()
            val fileView = render(tree, fileNode)
            val count = if (requests.size == 1) "1 request" else "${requests.size} requests"
            assertTrue(descendants(fileView).filterIsInstance<JLabel>().any { it.text == count })
            assertTrue(fileView.toolTipText.contains("Quick available:"))
            assertTrue(fileView.toolTipText.contains("Script required:"))
            for (requestNode in requests) {
                val request = requestNode.userObject as CollectionsSidebar.RequestNode
                val requestView = render(tree, requestNode)
                val label = if (request.mode == GuiMode.SCRIPT_ONLY) {
                    "Script required"
                } else "Quick available"
                assertTrue(descendants(requestView).filterIsInstance<JLabel>().any { it.text == label })
                assertTrue("Projection gaps must retain execution guidance", request.tooltip.contains("Follow Script"))
                assertTrue(requestView.toolTipText.startsWith("<html>"))
                assertTrue(requestView.toolTipText.contains("Follow Script"))
            }
        }
    }

    fun testEveryCapabilityFilterChangesScriptFilesAndKeepsDraftsAndRequestOrder() {
        addScripts()
        val service = project.getService(GuiDraftService::class.java)
        val existingDrafts = service.listDrafts().map { it.id }.toSet()
        val draft = service.saveDraft(service.createEmptyDraft().copy(title = "Saved draft"))
        try {
            val sidebar = CollectionsSidebar(project, { _, _, _, _ -> }, {})
            val tree = requestTree(sidebar)
            val combo = descendants(sidebar).filterIsInstance<JComboBox<*>>().single()
            val cases = listOf(
                FileProjectionFilter.ALL to setOf(
                    "editable.kttp.kts",
                    "send.kttp.kts",
                    "script.kttp.kts",
                    "mixed.kttp.kts"
                ),
                FileProjectionFilter.HAS_GUI to setOf("editable.kttp.kts", "mixed.kttp.kts"),
                FileProjectionFilter.HAS_SEND to setOf("send.kttp.kts", "mixed.kttp.kts"),
                FileProjectionFilter.ONLY_SCRIPT to setOf("script.kttp.kts"),
                FileProjectionFilter.MIXED to setOf("mixed.kttp.kts"),
                FileProjectionFilter.ALL to setOf(
                    "editable.kttp.kts",
                    "send.kttp.kts",
                    "script.kttp.kts",
                    "mixed.kttp.kts"
                )
            )
            for ((filter, expected) in cases) {
                combo.selectedItem = filter
                await("$filter changes the script list") { scriptNames(tree) == expected }
                assertEquals(existingDrafts + draft.id, draftIds(tree))
            }
            combo.selectedItem = FileProjectionFilter.HAS_GUI
            await("editable script filter") { scriptNames(tree) == setOf("editable.kttp.kts", "mixed.kttp.kts") }
            val mixed =
                projectFiles(tree).single { (it.userObject as CollectionsSidebar.FileNode).file.name == "mixed.kttp.kts" }
            tree.expandPath(TreePath(mixed.path))
            await("mixed requests retain declaration order") {
                mixed.children().asSequence().map { (it as DefaultMutableTreeNode).userObject }
                    .filterIsInstance<CollectionsSidebar.RequestNode>().map { it.displayUrl }.toList() ==
                    listOf("https://example.com/first", "https://example.com/second")
            }
        } finally {
            service.deleteDraft(draft.id)
        }
    }

    fun testTypingCombinesNameSearchWithTheFilterAndClearingRestoresTheResults() {
        addScripts()
        val service = project.getService(GuiDraftService::class.java)
        val existingDrafts = service.listDrafts().map { it.id }.toSet()
        val token = UUID.randomUUID().toString()
        val draft = service.saveDraft(service.createEmptyDraft().copy(title = "MiXeD $token"))
        try {
            val sidebar = CollectionsSidebar(project, { _, _, _, _ -> }, {})
            val tree = requestTree(sidebar)
            val search = descendants(sidebar).filterIsInstance<JTextField>().single()
            val combo = descendants(sidebar).filterIsInstance<JComboBox<*>>().single()
            await("initial script scan") { scriptNames(tree).size == 4 }
            search.text = "  MIXED  "
            await("case-insensitive filename search") { scriptNames(tree) == setOf("mixed.kttp.kts") }
            assertTrue("Matching drafts remain visible with the script search", draft.id in draftIds(tree))
            combo.selectedItem = FileProjectionFilter.ONLY_SCRIPT
            await("search and filter intersect") { scriptNames(tree).isEmpty() }
            assertTrue("The script-only filter must not hide a matching draft", draft.id in draftIds(tree))
            combo.selectedItem = FileProjectionFilter.ALL
            await("resetting filter retains search") { scriptNames(tree) == setOf("mixed.kttp.kts") }
            search.text = "  MIXED $token  "
            await("draft title search is case-insensitive and independent of filenames") {
                scriptNames(tree).isEmpty() && draftIds(tree) == setOf(draft.id)
            }
            search.text = "not-found-$token"
            await("no matching title or filename") { scriptNames(tree).isEmpty() && draftIds(tree).isEmpty() }
            search.text = ""
            await("clearing search restores results") { scriptNames(tree).size == 4 && draftIds(tree) == existingDrafts + draft.id }
        } finally {
            service.deleteDraft(draft.id)
        }
    }

    private fun addScripts() {
        myFixture.addFileToProject("editable.kttp.kts", "GET(\"https://example.com/editable\")")
        myFixture.addFileToProject("send.kttp.kts", "GET(\"https://example.com/send\") {}.then { assertStatus(200) }")
        myFixture.addFileToProject(
            "script.kttp.kts",
            "configureClient { followRedirects(false) }\nGET(\"https://example.com/script\")"
        )
        myFixture.addFileToProject(
            "mixed.kttp.kts",
            "GET(\"https://example.com/first\")\nGET(\"https://example.com/second\") {}.then { assertStatus(200) }"
        )
    }

    private fun projectFiles(tree: JTree): List<DefaultMutableTreeNode> {
        val root = tree.model.root as DefaultMutableTreeNode
        val group = root.children().asSequence().filterIsInstance<DefaultMutableTreeNode>()
            .firstOrNull { it.userObject == CollectionsSidebar.GroupNode.PROJECT } ?: return emptyList()
        return group.children().asSequence().filterIsInstance<DefaultMutableTreeNode>().toList()
    }

    private fun scriptNames(tree: JTree): Set<String> = projectFiles(tree)
        .map { (it.userObject as CollectionsSidebar.FileNode).file.name }.toSet()

    private fun draftIds(tree: JTree): Set<String> = (tree.model.root as DefaultMutableTreeNode)
        .depthFirstEnumeration().asSequence().filterIsInstance<DefaultMutableTreeNode>()
        .mapNotNull { (it.userObject as? CollectionsSidebar.DraftNode)?.draft?.id }.toSet()

    private fun requestTree(sidebar: CollectionsSidebar): JTree = descendants(sidebar)
        .filterIsInstance<JTree>().single { it.model.root.toString() == "Collections" }

    private fun render(tree: JTree, node: DefaultMutableTreeNode): JComponent = tree.cellRenderer
        .getTreeCellRendererComponent(tree, node, false, true, node.isLeaf, 0, false) as JComponent

    private fun await(description: String, condition: () -> Boolean) =
        PlatformTestUtil.waitWithEventsDispatching(description, condition, 10)

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
    }
}
