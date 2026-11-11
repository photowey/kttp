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

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.github.photowey.kttp.idea.path.KttpFileNavigation
import io.github.photowey.kttp.idea.toolwindow.CollectionsSidebar.GroupNode
import io.github.photowey.kttp.idea.toolwindow.CollectionsSidebar.ResponseFileNode
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * Response scanning is asynchronous; tree state is owned by the EDT.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal class SidebarResponses(
    private val project: Project,
    private val discovery: SidebarFileDiscovery,
    private val scheduler: SidebarTaskScheduler = ApplicationSidebarTaskScheduler
) {
    private val root = DefaultMutableTreeNode("Responses")
    private val model = DefaultTreeModel(root)
    private val tree = Tree(model).apply {
        isRootVisible = false
        showsRootHandles = true
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        rowHeight = 0
        cellRenderer = SidebarResponseTreeRenderer()
    }
    private val cards = CardLayout()
    private val content = JPanel(cards).apply {
        add(JLabel("No response files", SwingConstants.CENTER).apply {
            foreground = UIUtil.getContextHelpForeground()
        }, "empty")
        add(JBScrollPane(tree).apply { border = JBUI.Borders.empty() }, "tree")
    }
    private val header = JLabel("Responses", SwingConstants.LEADING).apply {
        useBoldUiFont()
        border = JBUI.Borders.empty(2, 2, 10, 2)
    }
    val component = JPanel(BorderLayout()).apply {
        add(header, BorderLayout.NORTH)
        add(content, BorderLayout.CENTER)
    }
    private var loaded = false
    private var sequence = 0

    init {
        installListeners()
    }

    fun refresh() {
        if (project.isDisposed) return
        val request = ++sequence
        scheduler.background {
            if (project.isDisposed) return@background
            val files = discovery.responses()
            scheduler.publish {
                if (!project.isDisposed && request == sequence) render(files)
            }
        }
    }

    private fun render(files: SidebarFileGroups) {
        val expanded = tree.getExpandedDescendants(TreePath(root.path))?.toList().orEmpty()
            .mapNotNull { (it.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? GroupNode }.toSet()
        val selected =
            (tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? ResponseFileNode
        root.removeAllChildren()
        val projectGroup = group(GroupNode.PROJECT, files.project)
        val kttpGroup = group(GroupNode.KTPP, files.kttp)
        root.add(projectGroup)
        root.add(kttpGroup)
        model.reload(root)
        for (group in listOf(projectGroup, kttpGroup)) {
            if (!loaded || (group.userObject as GroupNode) in expanded) tree.expandPath(TreePath(group.path))
        }
        if (selected != null) {
            root.depthFirstEnumeration().asSequence().filterIsInstance<DefaultMutableTreeNode>().firstOrNull {
                (it.userObject as? ResponseFileNode)?.file?.path == selected.file.path
            }?.let { tree.selectionPath = TreePath(it.path) }
        }
        val count = files.project.size + files.kttp.size
        header.text = "Responses ($count)"
        cards.show(content, if (count == 0) "empty" else "tree")
        loaded = true
    }

    private fun group(group: GroupNode, files: List<com.intellij.openapi.vfs.VirtualFile>): DefaultMutableTreeNode =
        DefaultMutableTreeNode(group).apply {
            files.filter { it.isValid }.forEach { add(DefaultMutableTreeNode(ResponseFileNode(it))) }
        }

    private fun installListeners() {
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) openAt(tree.getPathForLocation(event.x, event.y))
            }

            override fun mousePressed(event: MouseEvent) {
                if (event.isPopupTrigger) showMenu(event)
            }

            override fun mouseReleased(event: MouseEvent) {
                if (event.isPopupTrigger) showMenu(event)
            }
        })
        tree.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open-selected-response")
        tree.actionMap.put("open-selected-response", object : AbstractAction() {
            override fun actionPerformed(event: java.awt.event.ActionEvent?) {
                openAt(tree.selectionPath)
            }
        })
    }

    private fun showMenu(event: MouseEvent) {
        val path = tree.getPathForLocation(event.x, event.y) ?: return
        if ((path.lastPathComponent as? DefaultMutableTreeNode)?.userObject !is ResponseFileNode) return
        tree.selectionPath = path
        JPopupMenu().apply {
            add(JMenuItem("Open").apply { addActionListener { openAt(path) } })
            show(tree, event.x, event.y)
        }
    }

    private fun openAt(path: TreePath?) {
        val item = (path?.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? ResponseFileNode ?: return
        if (item.file.isValid && !project.isDisposed) KttpFileNavigation.open(project, item.file)
    }
}
