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
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Groups the sidebar's request creation, refresh and import controls using native themed components.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal class SidebarActionsPanel(
    onNewDraft: () -> Unit, onRefresh: () -> Unit, onAddRequest: () -> Unit, importButton: JComponent
) : JPanel(BorderLayout(4, 4)) {
    init {
        add(JPanel(BorderLayout(4, 0)).apply {
            add(JButton("New Draft", AllIcons.General.Add).apply {
                toolTipText = "Create a new detached GUI draft"
                addActionListener { onNewDraft() }
            }, BorderLayout.CENTER)
            add(JButton(AllIcons.Actions.Refresh).apply {
                isFocusable = false
                toolTipText = "Refresh drafts and scripts"
                addActionListener { onRefresh() }
            }, BorderLayout.EAST)
        }, BorderLayout.NORTH)
        add(JPanel(GridLayout(0, 1, 0, JBUI.scale(4))).apply {
            add(JButton("Add Request", AllIcons.General.Add).apply {
                toolTipText = "Append a request to the selected script, or create a temporary script"
                addActionListener { onAddRequest() }
            })
            add(importButton)
        }, BorderLayout.SOUTH)
    }
}
