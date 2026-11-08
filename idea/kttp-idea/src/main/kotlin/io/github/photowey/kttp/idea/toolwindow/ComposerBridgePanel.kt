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

import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.github.photowey.kttp.idea.gui.GuiMode
import java.awt.BorderLayout
import java.io.File
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.JTextArea

/**
 * Gives script preview a full-height workspace; projection diagnostics remain available on a separate details tab.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class ComposerBridgePanel : JPanel(BorderLayout()) {
    private val tabs = JTabbedPane()
    private val sourceSummaryArea = createTextArea(5)
    private val blockingGapArea = createTextArea(3)
    private val advisoryGapArea = createTextArea(3)
    private val summaryArea = createTextArea(2)
    private val linkedScriptLabel = JLabel("No linked script").apply {
        foreground = UIUtil.getContextHelpForeground()
        border = JBUI.Borders.empty(6, 4, 8, 4)
    }
    private val scriptPreviewArea = object : JTextArea(0, 0) {
        override fun updateUI() {
            super.updateUI()
            font = javax.swing.plaf.FontUIResource(
                java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, UIUtil.getLabelFont().size)
            )
        }
    }.apply {
        isEditable = false
        border = JBUI.Borders.empty(6)
    }

    init {
        val script = JPanel(BorderLayout()).apply {
            add(linkedScriptLabel, BorderLayout.NORTH)
            add(scroll(scriptPreviewArea), BorderLayout.CENTER)
        }
        val details = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(4)
            add(section("Execution", summaryArea))
            add(section("Field sources", sourceSummaryArea))
            add(section("Blocking gaps", blockingGapArea))
            add(section("Advisories", advisoryGapArea))
        }
        tabs.addTab("Script", script)
        tabs.addTab("Details", scroll(details))
        add(tabs, BorderLayout.CENTER)
    }

    fun update(
        bridgeSummary: String,
        sourceSummary: String,
        blockingGaps: String,
        advisoryGaps: String,
        linkedScriptPath: String?,
        scriptPreview: String
    ) {
        linkedScriptLabel.text = linkedScriptPath?.let { "Preview · ${File(it).name}" } ?: "Preview · not linked to a file"
        linkedScriptLabel.toolTipText = linkedScriptPath?.let {
            "Generated HTTP projection of $it. Open Script to view the complete Kotlin source."
        } ?: "Generated HTTP preview. Generate Script to save and link this preview."
        sourceSummaryArea.text = sourceSummary
        blockingGapArea.text = blockingGaps
        advisoryGapArea.text = advisoryGaps
        summaryArea.text = bridgeSummary
        scriptPreviewArea.text = scriptPreview
        scriptPreviewArea.caretPosition = 0
    }

    fun focusForMode(mode: GuiMode) {
        tabs.selectedIndex = if (mode == GuiMode.GUI_READY) 0 else 1
    }

    private fun createTextArea(rows: Int) = JTextArea(rows, 0).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        font = UIUtil.getLabelFont()
        border = JBUI.Borders.empty(6)
    }

    private fun scroll(content: javax.swing.JComponent) = JBScrollPane(content).apply {
        border = JBUI.Borders.empty()
        minimumSize = java.awt.Dimension(0, 0)
    }

    private fun section(title: String, content: JTextArea) = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(6, 2, 10, 2)
        add(JLabel(title).apply { useBoldUiFont() }, BorderLayout.NORTH)
        add(content, BorderLayout.CENTER)
    }
}
