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

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import io.github.photowey.kttp.idea.importing.ImportBundle
import io.github.photowey.kttp.idea.importing.ImportDestination
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

/**
 * Keeps entry-specific defaults while offering an editable project-local new-script destination.
 * Target changes invalidate the reviewed preview before any files are created.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/14
 */
internal class ImportDestinationControls(private val project: Project, private val original: ImportDestination) :
    JPanel(BorderLayout(0, JBUI.scale(6))) {
    internal val target =
        ComboBox(arrayOf(if (original is ImportDestination.Script) "Current Script" else "GUI Drafts", "New Script"))
    internal val directory =
        JBTextField(project.basePath?.let { Path.of(it, ".kttp", "imports").toString() }.orEmpty(), 30)
    internal val fileName = JBTextField("postman-import.kttp.kts", 22)
    private val newFile = JPanel(GridBagLayout())
    var onChanged: () -> Unit = {}
    private var suggestedName = fileName.text
    private var suggesting = false

    init {
        add(JPanel(GridBagLayout()).apply { formRow(this, 0, "Import into", target) }, BorderLayout.NORTH)
        val browse = JButton("Browse…").apply {
            toolTipText = "Choose the folder for the new script and its environment file"
            addActionListener {
                FileChooser.chooseFile(
                    FileChooserDescriptorFactory.createSingleFolderDescriptor(), project,
                    ImportSourceLocations.initialSelection(project, directory.text)
                ) { directory.text = it.path }
            }
        }
        formRow(newFile, 0, "Folder", directory, browse)
        formRow(newFile, 1, "File", fileName)
        directory.toolTipText = directory.text
        directory.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                directory.toolTipText = directory.text
            }
        })
        newFile.isVisible = false
        add(newFile, BorderLayout.CENTER)
        target.addActionListener { newFile.isVisible = target.selectedIndex == 1; revalidate(); onChanged() }
        val listener = object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (!suggesting) onChanged()
            }
        }
        directory.document.addDocumentListener(listener)
        fileName.document.addDocumentListener(listener)
    }

    private fun formRow(panel: JPanel, row: Int, text: String, field: javax.swing.JComponent, action: JButton? = null) {
        val inset = if (row == 0) 0 else 6
        panel.add(
            object : JLabel(text) {
                override fun getPreferredSize(): Dimension = super.getPreferredSize().apply {
                    width = maxOf(width, getFontMetrics(font).stringWidth("Import into") + JBUI.scale(4))
                }

                override fun getMinimumSize(): Dimension = preferredSize
            }.apply { labelFor = field },
            GridBagConstraints().apply {
                gridx = 0; gridy = row; anchor = GridBagConstraints.WEST; insets = JBUI.insets(inset, 0, 0, 8)
            })
        panel.add(field, GridBagConstraints().apply {
            gridx = 1; gridy = row; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
            gridwidth = if (action == null) 2 else 1; insets = JBUI.insetsTop(inset)
        })
        action?.let {
            panel.add(
                it,
                GridBagConstraints().apply { gridx = 2; gridy = row; insets = JBUI.insets(inset, 8, 0, 0) })
        }
    }

    fun destination(): ImportDestination = if (target.selectedIndex == 1)
        ImportDestination.NewScript(directory.text.trim(), fileName.text.trim()) else original

    fun suggest(bundle: ImportBundle) {
        if (fileName.text != suggestedName) return
        val name = bundle.sourceName?.lowercase()?.replace(Regex("[^\\p{L}\\p{N}._-]+"), "-")?.trim('.', '-')
            ?.takeIf(String::isNotBlank) ?: "imported-requests"
        suggestedName = "$name.kttp.kts"
        suggesting = true
        try {
            fileName.text = suggestedName
        } finally {
            suggesting = false
        }
    }

    fun error(): String? {
        if (target.selectedIndex != 1) return null
        if (runCatching { Path.of(directory.text.trim()).isAbsolute }.getOrDefault(false)
                .not()
        ) return "Choose an absolute destination directory."
        val name = fileName.text.trim()
        return if (!name.endsWith(".kttp.kts") || name.removeSuffix(".kttp.kts").isBlank() ||
            name.any { it == '/' || it == '\\' || it.isISOControl() }
        ) "Choose a .kttp.kts file name without directory separators." else null
    }
}
