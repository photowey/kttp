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
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Name search applies to all sources; the capability filter applies to script files.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal class SidebarSearchControls : JPanel(BorderLayout(0, JBUI.scale(4))) {
    val search = SearchTextField(false).apply {
        textEditor.apply {
            columns = 0
            emptyText.text = "Search drafts and scripts"
            toolTipText = "Search draft titles and script file names"
            accessibleContext.accessibleName = "Search drafts and scripts"
        }
    }

    val filter = JComboBox(FileProjectionFilter.entries.toTypedArray()).apply {
        selectedItem = FileProjectionFilter.ALL
        accessibleContext.accessibleName = "Quick Request capability filter"
        addActionListener { updateFilterDescription() }
    }

    init {
        add(search, BorderLayout.NORTH)
        add(JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            add(JLabel("Quick support", AllIcons.General.Filter, JLabel.LEADING).apply {
                labelFor = filter
                toolTipText =
                    "Filter scripts by Quick Request support. Follow Script can run requests in every category; drafts are unaffected."
            }, BorderLayout.WEST)
            add(filter, BorderLayout.CENTER)
        }, BorderLayout.CENTER)
        updateFilterDescription()
    }

    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)

    private fun updateFilterDescription() {
        val selection = filter.selectedItem as? FileProjectionFilter ?: FileProjectionFilter.ALL
        filter.toolTipText = "${selection.description} Drafts are unaffected."
        filter.accessibleContext.accessibleDescription = filter.toolTipText
    }
}
