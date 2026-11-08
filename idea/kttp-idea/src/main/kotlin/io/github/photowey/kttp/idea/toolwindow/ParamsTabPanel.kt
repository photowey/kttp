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
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.KeyStroke
import javax.swing.table.DefaultTableModel

/**
 * Params 标签页：编辑查询参数。
 * 使用 Key / Value 表格编辑查询参数；空键行不计入请求，列表接口保留重复键。
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/21
 */
class ParamsTabPanel : JPanel(BorderLayout()) {

    private val summaryLabel = JLabel("Sources: none").apply {
        foreground = UIUtil.getContextHelpForeground()
        border = JBUI.Borders.emptyBottom(4)
    }

    private val tableModel = object : DefaultTableModel(arrayOf("Key", "Value"), 0) {
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true
    }

    private val table = JTable(tableModel).apply {
        putClientProperty("terminateEditOnFocusLost", true)
        setShowGrid(true)
        fitRowHeightToFont()
        tableHeader.reorderingAllowed = false
        columnModel.getColumn(0).preferredWidth = 180
        columnModel.getColumn(1).preferredWidth = 260

        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (selectedRow < 0) return
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    val row = selectedRow
                    val key = getValueAt(row, 0) as? String ?: ""
                    val value = getValueAt(row, 1) as? String ?: ""
                    if (key.isNotBlank() || value.isNotBlank()) {
                        addEmptyRow()
                        e.consume()
                    }
                }
            }
        })
    }

    private val addParameterButton = JButton("Add Parameter").apply {
        addActionListener { addEmptyRow() }
    }

    init {
        border = JBUI.Borders.empty(4)
        add(summaryLabel, BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)

        val downKey = "addRowDown"
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), downKey)
        table.actionMap.put(downKey, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                if (table.selectedRow < 0) return
                if (table.selectedRow == tableModel.rowCount - 1) addEmptyRow()
                else table.setRowSelectionInterval(table.selectedRow + 1, table.selectedRow + 1)
            }
        })

        val toolbar = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(4)
            add(addParameterButton, BorderLayout.WEST)
        }
        add(toolbar, BorderLayout.SOUTH)
        addEmptyRow()
        refreshSummary()
    }

    private fun addEmptyRow() {
        if (!commitPendingEdits()) return
        tableModel.addRow(arrayOf("", ""))
        val newRowIndex = tableModel.rowCount - 1
        table.setRowSelectionInterval(newRowIndex, newRowIndex)
        table.scrollRectToVisible(table.getCellRect(newRowIndex, 0, true))
        if (table.isEnabled) table.editCellAt(newRowIndex, 0)
        refreshSummary()
    }

    /** Commits the active parameter cell before an action reads rows; false means the editor rejected the value. */
    fun commitPendingEdits(): Boolean = !table.isEditing || table.cellEditor.stopCellEditing()

    fun getEnabledParams(): Map<String, String> = getParamEntries().toMap()

    fun getParamEntries(): List<Pair<String, String>> {
        val params = mutableListOf<Pair<String, String>>()
        for (i in 0 until tableModel.rowCount) {
            val key = (tableModel.getValueAt(i, 0) as? String ?: "").trim()
            val value = tableModel.getValueAt(i, 1) as? String ?: ""
            if (key.isNotBlank()) {
                params.add(key to value)
            }
        }
        return params
    }

    fun setParams(params: Map<String, String>) = setParamEntries(params.toList())

    fun setParamEntries(params: List<Pair<String, String>>) {
        table.cellEditor?.cancelCellEditing()
        tableModel.rowCount = 0
        params.forEach { (key, value) ->
            tableModel.addRow(arrayOf(key, value))
        }
        addEmptyRow()
        refreshSummary()
    }

    fun deleteSelectedRow() {
        if (!table.isEnabled) return
        val selectedRow = table.selectedRow
        if (selectedRow >= 0 && tableModel.rowCount > 1) {
            tableModel.removeRow(selectedRow)
            refreshSummary()
        }
    }

    fun getTableModel(): javax.swing.table.TableModel = tableModel

    fun setEditable(editable: Boolean) {
        if (!editable) table.cellEditor?.cancelCellEditing()
        table.isEnabled = editable
        addParameterButton.isEnabled = editable
    }

    fun updateValueSourceSummary(summary: String) {
        summaryLabel.text = summary
    }

    private fun refreshSummary() {
        val envCount = getParamEntries().count { it.second.trim().startsWith("\${env:") }
        summaryLabel.text = if (envCount == 0) "Sources: literal values" else "Sources: $envCount env reference(s)"
    }
}
