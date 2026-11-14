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
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.github.photowey.kttp.idea.importing.ImportFormat
import io.github.photowey.kttp.idea.importing.ImportInput
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Font
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.Box
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Captures an immutable source choice on the EDT and reads selected UTF-8 files in the worker.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal data class ImportSourceSnapshot(
    val format: ImportFormat,
    val request: ImportTextSnapshot,
    val environment: ImportTextSnapshot?,
    val baseDirectory: String
) {
    fun read(): ImportInput {
        val directory = baseDirectory.trim().takeIf { it.isNotEmpty() }
            ?: request.filePath()?.parent?.toString()
            ?: environment?.filePath()?.parent?.toString()
        return ImportInput(request.read(), environment?.read()?.takeIf { it.isNotBlank() }, directory)
    }
}

/**
 * A text/file switch retained independently for each source field.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal data class ImportTextSnapshot(val text: String, val file: String, val fromFile: Boolean) {
    fun filePath(): Path? = file.trim().takeIf { fromFile && it.isNotEmpty() }?.let(Path::of)
    fun read(): String = if (fromFile) filePath()?.let { Files.readString(it) } ?: "" else text
}

/**
 * Native source controls shared by the script and GUI import entries.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal class ImportSourceControls(private val project: Project, initialFormat: ImportFormat) :
    JPanel(BorderLayout()) {
    var onEdited: () -> Unit = {}
    private val format = ComboBox(ImportFormat.entries.toTypedArray()).apply { selectedItem = initialFormat }
    private val baseDirectory = JTextField(project.basePath.orEmpty()).apply {
        accessibleContext.accessibleName = "Base directory"
    }
    private val curl = ImportTextSource(project, false, "cURL command", ::edited) { baseDirectory.text }
    private val collection =
        ImportTextSource(
            project,
            true,
            "Collection (optional for environment-only import)",
            ::edited
        ) { baseDirectory.text }
    private val environment = ImportTextSource(project, true, "Environment (optional)", ::edited) { baseDirectory.text }
    private val cards = CardLayout()
    private val sourceCards = ActiveImportCardPanel(cards).apply {
        add(curl, ImportFormat.CURL.name)
        add(Box.createVerticalBox().apply {
            add(collection)
            add(Box.createVerticalStrut(JBUI.scale(16)))
            add(environment)
        }, ImportFormat.POSTMAN.name)
    }

    init {
        border = JBUI.Borders.emptyBottom(8)
        add(JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            border = JBUI.Borders.emptyBottom(8)
            add(JLabel("Format").apply { labelFor = format }, BorderLayout.WEST)
            add(format, BorderLayout.CENTER)
        }, BorderLayout.NORTH)
        add(sourceCards, BorderLayout.CENTER)
        add(JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            border = JBUI.Borders.emptyTop(8)
            add(JLabel("Base directory").apply { labelFor = baseDirectory }, BorderLayout.NORTH)
            add(baseDirectory.apply {
                toolTipText =
                    "Resolve relative uploads here (current project by default); clear to use the source file's directory"
                document.addDocumentListener(editListener(::edited))
            }, BorderLayout.CENTER)
            add(JButton("Browse…").apply {
                addActionListener {
                    FileChooser.chooseFile(
                        FileChooserDescriptorFactory.createSingleFolderDescriptor(), project,
                        ImportSourceLocations.initialSelection(project, baseDirectory.text)
                    ) {
                        baseDirectory.text = it.path
                    }
                }
            }, BorderLayout.EAST)
        }, BorderLayout.SOUTH)
        cards.show(sourceCards, initialFormat.name)
        format.addActionListener {
            cards.show(sourceCards, selectedFormat().name)
            revalidate()
            edited()
        }
    }

    fun snapshot(): ImportSourceSnapshot = ImportSourceSnapshot(
        selectedFormat(), if (selectedFormat() == ImportFormat.CURL) curl.snapshot() else collection.snapshot(),
        if (selectedFormat() == ImportFormat.POSTMAN) environment.snapshot() else null, baseDirectory.text
    )

    fun preferredFocus(): javax.swing.JComponent =
        if (selectedFormat() == ImportFormat.CURL) curl.preferredFocus() else collection.preferredFocus()

    private fun selectedFormat(): ImportFormat = format.selectedItem as ImportFormat
    private fun edited() = onEdited()
}

/**
 * Offers pasted text or a local JSON/command file without reading it on the EDT.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
private class ImportTextSource(
    project: Project, initiallyFile: Boolean, label: String, onEdited: () -> Unit, baseDirectory: () -> String
) :
    JPanel(BorderLayout()) {
    private val mode = ComboBox(arrayOf("Text", "File")).apply {
        selectedIndex = if (initiallyFile) 1 else 0
        accessibleContext.accessibleName = "$label source type"
    }
    private val text = JBTextArea(5, 36).apply {
        lineWrap = true
        wrapStyleWord = true
        document.addDocumentListener(editListener(onEdited))
        accessibleContext.accessibleName = "$label text"
    }
    private val file = JTextField().apply {
        document.addDocumentListener(editListener(onEdited))
        accessibleContext.accessibleName = "$label file"
    }
    private val cards = CardLayout()
    private val inputs = ActiveImportCardPanel(cards)

    init {
        val paste = JButton("Paste").apply { addActionListener { this@ImportTextSource.text.paste() } }
        add(JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            border = JBUI.Borders.emptyBottom(4)
            add(JLabel(label.substringBefore(" (")).apply {
                font = font.deriveFont(Font.BOLD)
                labelFor = mode
            }, BorderLayout.CENTER)
            add(mode, BorderLayout.EAST)
        }, BorderLayout.NORTH)
        inputs.add(JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            add(JBScrollPane(text), BorderLayout.CENTER)
            add(JPanel(BorderLayout()).apply { add(paste, BorderLayout.EAST) }, BorderLayout.SOUTH)
        }, "Text")
        inputs.add(JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            add(JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
                add(file, BorderLayout.CENTER)
                add(JButton("Browse…").apply {
                    addActionListener {
                        FileChooser.chooseFile(
                            FileChooserDescriptor(true, false, false, false, false, false),
                            project,
                            ImportSourceLocations.initialSelection(project, file.text, baseDirectory())
                        ) {
                            file.text = it.path
                        }
                    }
                }, BorderLayout.EAST)
            }, BorderLayout.NORTH)
            add(
                JLabel(
                    if (label.startsWith("Environment")) "Optional · import variable values" else
                        "UTF-8 file · or switch to pasted text"
                ).apply {
                    foreground = UIUtil.getContextHelpForeground()
                }, BorderLayout.SOUTH
            )
        }, "File")
        add(inputs, BorderLayout.CENTER)
        cards.show(inputs, if (initiallyFile) "File" else "Text")
        mode.addActionListener {
            cards.show(inputs, mode.selectedItem as String)
            revalidate()
            onEdited()
        }
    }

    fun snapshot(): ImportTextSnapshot = ImportTextSnapshot(text.text, file.text, mode.selectedIndex == 1)

    fun preferredFocus(): javax.swing.JComponent = if (mode.selectedIndex == 1) file else text

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
}

/**
 * Sizes the form to its visible source card so inactive text editors do not leave blank space in file mode.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/14
 */
private class ActiveImportCardPanel(layout: CardLayout) : JPanel(layout) {
    override fun getPreferredSize(): Dimension = components.firstOrNull { it.isVisible }?.preferredSize
        ?: super.getPreferredSize()
}

/**
 * Adapts all Swing document changes to the same input invalidation hook.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
private fun editListener(onEdited: () -> Unit): DocumentListener = object : DocumentListener {
    override fun insertUpdate(e: DocumentEvent?) = onEdited()
    override fun removeUpdate(e: DocumentEvent?) = onEdited()
    override fun changedUpdate(e: DocumentEvent?) = onEdited()
}
