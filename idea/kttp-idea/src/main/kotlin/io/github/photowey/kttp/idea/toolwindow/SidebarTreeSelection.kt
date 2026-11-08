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

import com.intellij.openapi.vfs.VirtualFile
import io.github.photowey.kttp.idea.toolwindow.CollectionsSidebar.DraftNode
import io.github.photowey.kttp.idea.toolwindow.CollectionsSidebar.FileNode
import io.github.photowey.kttp.idea.toolwindow.CollectionsSidebar.GroupNode
import io.github.photowey.kttp.idea.toolwindow.CollectionsSidebar.RequestNode
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

/**
 * Captures expanded groups and files together with the selected sidebar item.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal data class SidebarCollectionTreeState(
    val expandedGroups: Set<GroupNode>,
    val expandedFiles: Set<String>,
    val selectedKey: String?
)

/**
 * Stable keys restore visual selection without reopening an editor or replacing its working copy.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal class SidebarTreeSelection(
    private val tree: JTree,
    private val rootNode: DefaultMutableTreeNode,
    private val selectedFile: (VirtualFile?) -> Unit
) {
    var restoring = false
        private set

    fun capture(): SidebarCollectionTreeState {
        val expandedPaths = tree.getExpandedDescendants(TreePath(rootNode.path))?.toList().orEmpty()
        val expandedGroups = expandedPaths.mapNotNull { path ->
            ((path.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? GroupNode)
        }.toSet()
        val expandedFiles = expandedPaths.mapNotNull { path ->
            ((path.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? FileNode)?.file?.path
        }.toSet()
        return SidebarCollectionTreeState(
            expandedGroups = expandedGroups,
            expandedFiles = expandedFiles,
            selectedKey = tree.selectionPath?.let(::collectionNodeKey)
        )
    }

    private fun collectionNodeKey(path: TreePath): String? {
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return null
        return when (val userObject = node.userObject) {
            is GroupNode -> "group:${userObject.name}"
            is DraftNode -> "draft:${userObject.draft.id}"
            is FileNode -> "file:${userObject.file.path}"
            is RequestNode -> {
                val parent = path.parentPath?.lastPathComponent as? DefaultMutableTreeNode ?: return null
                val file = parent.userObject as? FileNode ?: return null
                "request:${file.file.path}:${parent.getIndex(node)}"
            }

            else -> null
        }
    }

    fun find(key: String): TreePath? {
        fun search(node: DefaultMutableTreeNode): TreePath? {
            val path = TreePath(node.path)
            if (collectionNodeKey(path) == key) return path
            for (index in 0 until node.childCount) {
                search(node.getChildAt(index) as DefaultMutableTreeNode)?.let { return it }
            }
            return null
        }
        return search(rootNode)
    }

    fun restore(path: TreePath) {
        restoring = true
        try {
            tree.selectionPath = path
        } finally {
            restoring = false
        }
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        selectedFile(
            when (val userObject = node.userObject) {
                is FileNode -> userObject.file
                is RequestNode -> {
                    val parent = path.parentPath?.lastPathComponent as? DefaultMutableTreeNode
                    (parent?.userObject as? FileNode)?.file
                }

                else -> null
            }
        )
    }

}
