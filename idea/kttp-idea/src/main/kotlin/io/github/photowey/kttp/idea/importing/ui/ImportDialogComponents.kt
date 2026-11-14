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

import com.intellij.ui.components.JBTextArea
import java.awt.Component
import java.awt.Container
import javax.swing.table.AbstractTableModel

/**
 * Presents request checkboxes while preventing selection of entries with fatal diagnostics.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal class RequestRows(private val importState: ImportDialogState, private val onSelected: (Int, Boolean) -> Unit) :
    AbstractTableModel() {
    private val columns = arrayOf("Import", "Request", "Method", "URL", "Source")
    override fun getRowCount(): Int = importState.bundle?.requests?.size ?: 0
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]
    override fun getColumnClass(columnIndex: Int): Class<*> =
        if (columnIndex == 0) Boolean::class.javaObjectType else String::class.java

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean =
        columnIndex == 0 && importState.bundle?.requests?.getOrNull(rowIndex)?.selectable == true

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val request = importState.bundle!!.requests[rowIndex]
        return when (columnIndex) {
            0 -> rowIndex in importState.selected
            1 -> request.name
            2 -> request.method
            3 -> request.url.text
            else -> request.sourcePath
        }
    }

    override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
        if (columnIndex == 0 && value is Boolean) onSelected(rowIndex, value)
    }
}

internal fun readOnlyArea(rows: Int): JBTextArea = JBTextArea(rows, 72).apply {
    isEditable = false
    lineWrap = true
    wrapStyleWord = true
}

internal fun enableTree(component: Component, enabled: Boolean) {
    component.isEnabled = enabled
    if (component is Container) component.components.forEach { enableTree(it, enabled) }
}
