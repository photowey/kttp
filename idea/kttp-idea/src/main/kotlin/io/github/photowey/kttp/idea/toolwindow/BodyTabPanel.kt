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

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.photowey.kttp.idea.gui.BodySpec
import io.github.photowey.kttp.idea.gui.GuiValueRefCodec
import io.github.photowey.kttp.idea.gui.NameValueRef
import io.github.photowey.kttp.idea.gui.ValueRef
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * Body 标签页：编辑请求体
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/21
 */
class BodyTabPanel : JPanel(BorderLayout()) {

    private val summaryLabel = JLabel("Sources: none").apply {
        foreground = UIUtil.getContextHelpForeground()
        border = JBUI.Borders.emptyBottom(4)
    }

    /**
     * Maps composer body modes to their labels and persisted legacy names.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/21
     */
    private enum class BodyType(val legacyName: String, private val label: String) {
        NONE("none", "None"), JSON("json", "JSON"), FORM("form", "x-www-form-urlencoded"), RAW("raw", "Raw");

        override fun toString(): String = label
    }

    private val bodyTypeComboBox = JComboBox(BodyType.entries.toTypedArray())
    private var loadedBody: BodySpec = BodySpec.None

    private val jsonTextArea = JTextArea().apply {
        tabSize = 2
    }

    private val formDataTableModel = object : DefaultTableModel(
        arrayOf("Key", "Value"),
        0
    ) {
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true
    }

    private val formDataTable = JTable(formDataTableModel).apply {
        putClientProperty("terminateEditOnFocusLost", true)
        setShowGrid(true)
        fitRowHeightToFont()
        tableHeader.reorderingAllowed = false
        columnModel.getColumn(0).preferredWidth = 200
        columnModel.getColumn(1).preferredWidth = 300
    }

    private val rawTextArea = JTextArea()

    private val beautifyJsonButton = JButton("Beautify JSON").apply {
        addActionListener { beautifyJson() }
    }

    private val addFieldButton = JButton("Add Field").apply {
        addActionListener {
            if (commitPendingEdits()) {
                formDataTableModel.addRow(arrayOf("", ""))
                val row = formDataTableModel.rowCount - 1
                formDataTable.setRowSelectionInterval(row, row)
                formDataTable.scrollRectToVisible(formDataTable.getCellRect(row, 0, true))
                formDataTable.editCellAt(row, 0)
            }
        }
    }

    private val formPanel = JPanel(BorderLayout()).apply {
        add(JBScrollPane(formDataTable), BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 4)).apply {
            add(addFieldButton)
        }, BorderLayout.SOUTH)
    }

    private val cardLayout = CardLayout()
    private val contentPanel = JPanel(cardLayout).apply {
        add(JLabel("No body"), "none")
        add(JBScrollPane(jsonTextArea), "json")
        add(formPanel, "form")
        add(JBScrollPane(rawTextArea), "raw")
    }

    init {
        border = JBUI.Borders.empty(4)

        // 顶部：Body Type 选择器
        val topPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4)).apply {
            add(JLabel("Body Type:"))
            add(bodyTypeComboBox)
            add(beautifyJsonButton)
        }
        val north = JPanel(BorderLayout()).apply {
            add(topPanel, BorderLayout.NORTH)
            add(summaryLabel, BorderLayout.SOUTH)
        }
        add(north, BorderLayout.NORTH)
        add(contentPanel, BorderLayout.CENTER)

        // Body Type 变化时切换内容面板
        bodyTypeComboBox.addActionListener {
            updateContentPanel()
        }

        formDataTableModel.addRow(arrayOf("", ""))
        updateContentPanel()
        refreshSummary()
    }

    private fun updateContentPanel() {
        val type = bodyTypeComboBox.selectedItem as BodyType
        beautifyJsonButton.isEnabled = bodyTypeComboBox.isEnabled && type == BodyType.JSON
        cardLayout.show(contentPanel, type.legacyName)
    }

    private fun beautifyJson() {
        if (bodyTypeComboBox.selectedItem == BodyType.JSON) {
            val text = jsonTextArea.text.trim()
            if (text.isNotBlank()) {
                try {
                    val mapper = ObjectMapper()
                    val jsonNode = mapper.readTree(text)
                    jsonTextArea.text = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode)
                } catch (e: Exception) {
                    // 不是有效的 JSON，不处理
                }
            }
        }
    }

    /** Legacy encoded form adapter. Request binding uses getBodySpec instead. */
    fun getBody(): Pair<String, String> {
        val type = bodyTypeComboBox.selectedItem as BodyType
        val content = when (type) {
            BodyType.NONE -> ""
            BodyType.JSON -> jsonTextArea.text
            BodyType.FORM -> getFormDataFields().joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, StandardCharsets.UTF_8)}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
            }

            BodyType.RAW -> rawTextArea.text
        }
        return type.legacyName to content
    }

    /** Reads the structured body, retaining the loaded source reference when its displayed value is unchanged. */
    fun getBodySpec(valueParser: (String, Boolean) -> ValueRef = GuiValueRefCodec::parse): BodySpec {
        fun content(input: String): ValueRef {
            val previous = when (val body = loadedBody) {
                is BodySpec.Json -> body.content
                is BodySpec.Raw -> body.content
                else -> null
            }
            return previous?.takeIf { GuiValueRefCodec.toDisplayValue(it) == input } ?: valueParser(input, true)
        }
        return when (bodyTypeComboBox.selectedItem as BodyType) {
            BodyType.NONE -> BodySpec.None
            BodyType.JSON -> BodySpec.Json(content(jsonTextArea.text))
            BodyType.RAW -> BodySpec.Raw(content(rawTextArea.text), (loadedBody as? BodySpec.Raw)?.contentType)
            BodyType.FORM -> getFormDataFields().map { (name, value) -> NameValueRef(name, valueParser(value, false)) }
                .let { BodySpec.FormUrlEncoded(it) }
        }
    }

    /** Replaces all body editors from a structured value, clearing content left by the previous request. */
    fun setBodySpec(body: BodySpec) {
        clearEditors()
        when (body) {
            BodySpec.None -> bodyTypeComboBox.selectedItem = BodyType.NONE
            is BodySpec.Json -> {
                bodyTypeComboBox.selectedItem = BodyType.JSON
                jsonTextArea.text = GuiValueRefCodec.toDisplayValue(body.content)
            }

            is BodySpec.Raw -> {
                bodyTypeComboBox.selectedItem = BodyType.RAW
                rawTextArea.text = GuiValueRefCodec.toDisplayValue(body.content)
            }

            is BodySpec.FormUrlEncoded -> setFormDataFields(body.fields.map {
                it.name to GuiValueRefCodec.toDisplayValue(
                    it.value
                )
            })
        }
        loadedBody = body
        updateContentPanel()
        refreshSummary()
    }

    /**
     * 获取 Form Data 键值对列表（用于生成脚本）
     */
    fun getFormDataFields(): List<Pair<String, String>> {
        val fields = mutableListOf<Pair<String, String>>()
        for (i in 0 until formDataTableModel.rowCount) {
            val key = (formDataTableModel.getValueAt(i, 0) as? String ?: "").trim()
            val value = formDataTableModel.getValueAt(i, 1) as? String ?: ""
            if (key.isNotBlank()) {
                fields.add(key to value)
            }
        }
        return fields
    }

    /**
     * 设置 Body 内容
     */
    fun setBody(bodyType: String, content: String) {
        if (bodyType.lowercase() in listOf("form", "form-data", "x-www-form-urlencoded")) {
            val fields = content.split("&").filter { it.isNotBlank() }.map { pair ->
                val parts = pair.split("=", limit = 2)
                URLDecoder.decode(parts[0], StandardCharsets.UTF_8) to
                    URLDecoder.decode(parts.getOrElse(1) { "" }, StandardCharsets.UTF_8)
            }
            setFormDataFields(fields)
            return
        }
        clearEditors()
        when (bodyType.lowercase()) {
            "json" -> {
                bodyTypeComboBox.selectedItem = BodyType.JSON
                jsonTextArea.text = content
            }

            "raw" -> {
                bodyTypeComboBox.selectedItem = BodyType.RAW
                rawTextArea.text = content
            }

            else -> {
                bodyTypeComboBox.selectedItem = BodyType.NONE
            }
        }
        updateContentPanel()
        refreshSummary()
    }

    fun setFormDataFields(fields: List<Pair<String, String>>) {
        clearEditors()
        formDataTableModel.rowCount = 0
        fields.forEach { (key, value) -> formDataTableModel.addRow(arrayOf(key, value)) }
        formDataTableModel.addRow(arrayOf("", ""))
        bodyTypeComboBox.selectedItem = BodyType.FORM
        updateContentPanel()
        refreshSummary()
    }

    fun commitPendingEdits(): Boolean = !formDataTable.isEditing || formDataTable.cellEditor.stopCellEditing()

    private fun clearEditors() {
        loadedBody = BodySpec.None
        formDataTable.cellEditor?.cancelCellEditing()
        jsonTextArea.text = ""
        rawTextArea.text = ""
        formDataTableModel.rowCount = 0
        formDataTableModel.addRow(arrayOf("", ""))
    }

    private val changeListeners = mutableListOf<() -> Unit>()

    /**
     * 通知请求工作副本发生变化；脚本写入由调用方的显式操作触发。
     */
    fun addChangeListener(listener: () -> Unit) {
        changeListeners.add(listener)
        // 监听 JSON/Raw TextArea 变化
        jsonTextArea.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) {
                refreshSummary(); listener()
            }

            override fun removeUpdate(e: javax.swing.event.DocumentEvent) {
                refreshSummary(); listener()
            }

            override fun changedUpdate(e: javax.swing.event.DocumentEvent) {
                refreshSummary(); listener()
            }
        })
        rawTextArea.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) {
                refreshSummary(); listener()
            }

            override fun removeUpdate(e: javax.swing.event.DocumentEvent) {
                refreshSummary(); listener()
            }

            override fun changedUpdate(e: javax.swing.event.DocumentEvent) {
                refreshSummary(); listener()
            }
        })
        // 监听 FormData Table 变化
        formDataTableModel.addTableModelListener { refreshSummary(); listener() }
        // 监听 Body Type 变化
        bodyTypeComboBox.addActionListener { refreshSummary(); listener() }
    }

    fun setEditable(editable: Boolean) {
        if (!editable) formDataTable.cellEditor?.cancelCellEditing()
        bodyTypeComboBox.isEnabled = editable
        jsonTextArea.isEditable = editable
        formDataTable.isEnabled = editable
        rawTextArea.isEditable = editable
        addFieldButton.isEnabled = editable
        updateContentPanel()
    }

    fun updateValueSourceSummary(summary: String) {
        summaryLabel.text = summary
    }

    private fun refreshSummary() {
        val (_, content) = getBody()
        summaryLabel.text = when {
            content.trim().startsWith("\${env:") -> "Sources: env reference"
            content.trim().startsWith("@file:") -> "Sources: local file reference"
            content.isBlank() -> "Sources: none"
            else -> "Sources: literal content"
        }
    }
}
