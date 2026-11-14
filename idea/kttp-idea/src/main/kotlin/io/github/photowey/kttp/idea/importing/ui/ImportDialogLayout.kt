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

package io.github.photowey.kttp.idea.importing.ui

import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.github.photowey.kttp.idea.toolwindow.WorkspaceUi
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.ScrollPaneConstants

/**
 * Keeps import sources and destination options in shared columns, with resizable review panes and IDE theme surfaces.
 * Background work and import eligibility remain owned by the dialog controller.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/14
 */
internal class ImportDialogLayout {
    private val progress = JProgressBar().apply {
        isIndeterminate = true
        isVisible = false
        preferredSize = JBUI.size(56, 4)
        accessibleContext.accessibleName = "Preparing import"
    }

    fun create(
        source: JComponent, requests: JTable, target: JTextArea, environmentFolder: JButton,
        refresh: Action, cancelPreview: JButton, status: JLabel,
        script: JTextArea, diagnostics: JTextArea, retainedSource: JTextArea,
        destinationControls: JComponent, environmentSelection: JComponent
    ): JComponent {
        val inputs = section("Source", "Choose a format and supply request data.", JBScrollPane(source).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        })
        val review =
            section("Requests", "Select the requests to import after refreshing the preview.", JBScrollPane(requests))
        val preview = JTabbedPane().apply {
            addTab("Generated Script", JBScrollPane(script))
            addTab("Diagnostics", JBScrollPane(diagnostics))
            addTab("Retained Source", JBScrollPane(retainedSource))
            accessibleContext.accessibleName = "Import preview details"
        }
        val reviewPanes = WorkspaceUi.split(
            true, "Kttp.Import.Review.Proportion", 0.42f, review, WorkspaceUi.surface(preview, 10)
        )
        val right = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
            add(JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
                add(JLabel("Review import").apply { font = font.deriveFont(Font.BOLD) }, BorderLayout.CENTER)
                add(JPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, JBUI.scale(6), 0)).apply {
                    add(JButton(refresh))
                    add(cancelPreview)
                }, BorderLayout.EAST)
            }, BorderLayout.NORTH)
            add(reviewPanes, BorderLayout.CENTER)
        }
        val destinationOptions = optionGroup("Destination", JPanel(BorderLayout(0, JBUI.scale(8))).apply {
            add(destinationControls, BorderLayout.NORTH)
            add(target, BorderLayout.CENTER)
            add(environmentFolder, BorderLayout.SOUTH)
        })
        val environmentOptions = optionGroup("Environment", environmentSelection)
        return JPanel(BorderLayout(0, JBUI.scale(12))).apply {
            preferredSize = JBUI.size(1060, 680)
            minimumSize = JBUI.size(800, 540)
            add(
                WorkspaceUi.split(
                    false, "Kttp.Import.Source.Proportion", 0.34f,
                    column(inputs, destinationOptions, environmentOptions),
                    column(right, environmentOptions, destinationOptions)
                ), BorderLayout.CENTER
            )
            add(footer(status), BorderLayout.SOUTH)
        }
    }

    fun setBusy(busy: Boolean) {
        progress.isVisible = busy
    }

    private fun section(title: String, description: String, content: JComponent): JComponent =
        WorkspaceUi.surface(JPanel(BorderLayout(0, JBUI.scale(10))).apply {
            add(JPanel(BorderLayout(0, JBUI.scale(4))).apply {
                add(JLabel(title).apply { font = font.deriveFont(Font.BOLD) }, BorderLayout.NORTH)
                add(JTextArea(description, 2, 0).apply {
                    isEditable = false
                    isFocusable = false
                    isOpaque = false
                    lineWrap = true
                    wrapStyleWord = true
                    font = UIUtil.getLabelFont()
                    foreground = UIUtil.getContextHelpForeground()
                }, BorderLayout.CENTER)
            }, BorderLayout.NORTH)
            add(content, BorderLayout.CENTER)
        }, 12)

    private fun column(content: JComponent, options: JComponent, otherOptions: JComponent): JComponent =
        JPanel(BorderLayout(0, JBUI.scale(12))).apply {
            add(content, BorderLayout.CENTER)
            // Equal footer heights keep both horizontal boundaries aligned as target controls appear or wrap.
            add(object : JPanel(BorderLayout()) {
                override fun getPreferredSize(): Dimension = super.getPreferredSize().apply {
                    height = maxOf(options.preferredSize.height, otherOptions.preferredSize.height)
                }
            }.apply { add(options, BorderLayout.CENTER) }, BorderLayout.SOUTH)
        }

    private fun footer(status: JLabel): JComponent =
        JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            add(status, BorderLayout.CENTER)
            add(progress, BorderLayout.EAST)
        }

    private fun optionGroup(title: String, content: JComponent): JComponent =
        WorkspaceUi.surface(JPanel(BorderLayout(0, JBUI.scale(8))).apply {
            add(JLabel(title).apply { font = font.deriveFont(Font.BOLD) }, BorderLayout.NORTH)
            add(content, BorderLayout.CENTER)
        }, 12)
}
