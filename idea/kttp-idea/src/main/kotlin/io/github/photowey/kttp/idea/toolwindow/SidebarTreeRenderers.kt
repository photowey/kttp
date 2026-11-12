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

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.github.photowey.kttp.idea.toolwindow.CollectionsSidebar.DraftNode
import io.github.photowey.kttp.idea.toolwindow.CollectionsSidebar.FileNode
import io.github.photowey.kttp.idea.toolwindow.CollectionsSidebar.GroupNode
import io.github.photowey.kttp.idea.toolwindow.CollectionsSidebar.PlaceholderNode
import io.github.photowey.kttp.idea.toolwindow.CollectionsSidebar.RequestNode
import io.github.photowey.kttp.idea.toolwindow.CollectionsSidebar.ResponseFileNode
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer

/**
 * Render cached display values only; never resolve request PSI in a renderer.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal class SidebarRequestTreeRenderer(private val summary: (String) -> FileProjectionSummary?) : TreeCellRenderer {
    private val fileLabel = JLabel()
    private val filePanel = JPanel(BorderLayout())
    private val fileSummary = badgeLabel()
    private val requestPanel = JPanel(BorderLayout(JBUI.scale(6), 0))
    private val methodLabel = badgeLabel()
    private val modeLabel = badgeLabel()
    private val urlLabel = JLabel().apply { foreground = JBColor.GRAY }

    override fun getTreeCellRendererComponent(
        tree: JTree?, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
    ): Component {
        val node = value as? DefaultMutableTreeNode ?: return JLabel()
        val currentFont = tree?.font ?: UIUtil.getLabelFont()
        listOf(fileLabel, fileSummary, methodLabel, modeLabel, urlLabel).forEach { it.font = currentFont }
        val foreground = UIUtil.getTreeForeground(selected, hasFocus)
        fileLabel.foreground = foreground
        return when (val item = node.userObject) {
            PlaceholderNode -> JLabel("...").apply {
                font = currentFont
                this.foreground = UIUtil.getContextHelpForeground()
            }

            is GroupNode -> label("${item.displayName} (${node.childCount})", AllIcons.Nodes.Folder, selected, hasFocus)
                .apply { toolTipText = tooltip(item.description) }
            is DraftNode -> label(item.draft.title, AllIcons.Actions.MenuOpen, selected, hasFocus)
            is FileNode -> file(item, selected, hasFocus)
            is RequestNode -> request(item, selected, hasFocus)
            else -> JLabel(item?.toString().orEmpty()).apply { font = currentFont }
        }
    }

    private fun label(text: String, icon: javax.swing.Icon, selected: Boolean, focus: Boolean): JLabel =
        fileLabel.apply {
            this.text = text
            this.icon = icon
            toolTipText = null
            selection(this, selected, focus)
        }

    private fun file(item: FileNode, selected: Boolean, focus: Boolean): JPanel {
        val badge = summary(item.file.path)?.let(RequestProjectionPresentation::fileBadge)
        fileLabel.text = item.file.name
        fileLabel.icon = AllIcons.FileTypes.Any_type
        fileLabel.isOpaque = false
        fileLabel.toolTipText = null
        filePanel.removeAll()
        filePanel.add(fileLabel, BorderLayout.WEST)
        filePanel.toolTipText = badge?.tooltip?.let(::tooltip)
        if (badge != null) {
            fileSummary.text = badge.text
            fileSummary.background = badge.background
            fileSummary.foreground = if (selected) UIUtil.getTreeForeground(true, focus) else badge.foreground
            filePanel.add(fileSummary, BorderLayout.EAST)
        }
        selection(filePanel, selected, focus)
        return filePanel
    }

    private fun request(item: RequestNode, selected: Boolean, focus: Boolean): JPanel {
        val badge = RequestProjectionPresentation.badge(item.mode)
        val foreground = UIUtil.getTreeForeground(selected, focus)
        methodLabel.text = item.displayMethod
        methodLabel.foreground = if (selected) foreground else KttpUiColors.method(item.displayMethod)
        modeLabel.text = badge.text
        modeLabel.background = badge.background
        modeLabel.foreground = if (selected) foreground else badge.foreground
        urlLabel.text = item.displayUrl
        urlLabel.foreground = foreground
        requestPanel.toolTipText = tooltip(item.tooltip)
        requestPanel.removeAll()
        requestPanel.add(methodLabel, BorderLayout.WEST)
        requestPanel.add(urlLabel, BorderLayout.CENTER)
        requestPanel.add(modeLabel, BorderLayout.EAST)
        selection(requestPanel, selected, focus)
        return requestPanel
    }

    private fun badgeLabel(): JLabel = JLabel().apply {
        border = JBUI.Borders.empty(2, 4)
        isOpaque = false
    }

    /** Wraps explanations and escapes source-derived text before Swing renders it as HTML. */
    private fun tooltip(text: String): String =
        "<html><div style='width: ${JBUI.scale(360)}px'>${StringUtil.escapeXmlEntities(text).replace("\n", "<br>")}</div></html>"
}

/**
 * Renders saved response nodes using the current IDEA tree presentation.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal class SidebarResponseTreeRenderer : TreeCellRenderer {
    private val label = JLabel()
    override fun getTreeCellRendererComponent(
        tree: JTree?, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
    ): Component {
        val node = value as? DefaultMutableTreeNode ?: return label
        label.font = tree?.font ?: UIUtil.getLabelFont()
        when (val item = node.userObject) {
            is GroupNode -> {
                label.text = item.displayName; label.icon = AllIcons.Nodes.Folder
            }

            is ResponseFileNode -> {
                label.text = item.file.name; label.icon = AllIcons.FileTypes.Any_type
            }

            else -> {
                label.text = item?.toString().orEmpty(); label.icon = null
            }
        }
        label.foreground = UIUtil.getTreeForeground(selected, hasFocus)
        selection(label, selected, hasFocus)
        return label
    }
}

private fun selection(component: javax.swing.JComponent, selected: Boolean, focus: Boolean) {
    component.isOpaque = selected
    component.background = if (selected) UIUtil.getTreeSelectionBackground(focus) else null
}
