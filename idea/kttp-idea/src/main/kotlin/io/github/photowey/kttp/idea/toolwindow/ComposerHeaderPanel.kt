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
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.WrapLayout
import io.github.photowey.kttp.idea.gui.DraftOwnership
import io.github.photowey.kttp.idea.gui.GuiMode
import io.github.photowey.kttp.idea.execution.RequestExecutionMode
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * Presents request identity, target, execution controls, and transport options.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class ComposerHeaderPanel : JPanel() {

    val titleField = JTextField().apply {
        toolTipText = "Draft title"
        columns = 18
    }

    val requestNameField = JTextField().apply {
        toolTipText = "Optional request name for generated script"
        columns = 16
    }

    val methodComboBox = JComboBox<String>().apply {
        addItem("GET")
        addItem("POST")
        addItem("PUT")
        addItem("DELETE")
        addItem("PATCH")
        addItem("HEAD")
        addItem("OPTIONS")
        selectedItem = "GET"
    }

    val urlField = JTextField().apply {
        toolTipText = "Enter request URL"
        columns = 34
    }

    val executionModeCombo = JComboBox(RequestExecutionMode.entries.toTypedArray()).apply {
        selectedItem = RequestExecutionMode.QUICK_REQUEST
        accessibleContext.accessibleName = "Request execution mode"
    }
    val executionHint = JLabel("HTTP only; no then.").apply { foreground = UIUtil.getContextHelpForeground() }
    internal val requestProgress = RequestProgressPanel()

    val sendButton = JButton("Send")
    val saveDraftButton = JButton("Save Draft")
    val generateScriptButton = JButton("Generate Script")
    val openScriptButton = JButton("Open Script")
    val detachButton = JButton("Detach")

    val modeLabel = JLabel().apply {
        isOpaque = false
        border = JBUI.Borders.empty(4, 8)
        accessibleContext.accessibleName = "Request status"
    }

    val noRedirectCheck = JCheckBox("No Redirect")
    val noCookiesCheck = JCheckBox("No Cookies")
    val http2Check = JCheckBox("HTTP/2")

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false

        val titleRow = createRowPanel().apply {
            add(JLabel("Title").apply { labelFor = titleField })
            add(titleField)
            add(JLabel("Request name").apply { labelFor = requestNameField })
            add(requestNameField)
            add(modeLabel)
        }
        val requestRow = JPanel(GridBagLayout()).apply {
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
            border = JBUI.Borders.empty(6)
            add(methodComboBox, GridBagConstraints().apply {
                insets = JBUI.insetsRight(8)
            })
            add(urlField, GridBagConstraints().apply {
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insetsRight(8)
            })
            add(sendButton.apply { icon = AllIcons.Actions.Execute }, GridBagConstraints())
        }
        val actionsRow = createRowPanel().apply {
            add(executionModeCombo)
            add(saveDraftButton)
            add(generateScriptButton)
            add(openScriptButton)
            add(detachButton)
        }
        val optionsRow = createRowPanel().apply {
            add(noRedirectCheck)
            add(noCookiesCheck)
            add(http2Check)
            add(executionHint)
        }
        listOf(noRedirectCheck, noCookiesCheck, http2Check).forEach(::styleTransportToggle)
        styleField(titleField, 14)
        styleField(requestNameField, 12)
        styleField(urlField, 0)
        urlField.minimumSize = Dimension(JBUI.scale(80), urlField.preferredSize.height)
        add(titleRow)
        add(requestRow)
        add(actionsRow)
        add(optionsRow)
        add(requestProgress)
    }

    override fun getPreferredSize(): Dimension {
        val owner = parent
        val availableWidth = if (owner != null && owner.width > 0) {
            owner.width - owner.insets.left - owner.insets.right
        } else width
        if (availableWidth > 0) {
            // BorderLayout measures its NORTH component before assigning the resized width.
            // Give wrapping rows that width before BoxLayout asks for their preferred heights.
            val rowWidth = (availableWidth - insets.left - insets.right).coerceAtLeast(0)
            components.filterIsInstance<JPanel>().forEach { it.setSize(rowWidth, it.height) }
            (layout as? BoxLayout)?.invalidateLayout(this)
        }
        return super.getPreferredSize()
    }

    fun updateModeBadge(
        ownership: DraftOwnership,
        mode: GuiMode,
        dirty: Boolean,
        executionMode: RequestExecutionMode = RequestExecutionMode.QUICK_REQUEST
    ) {
        val sourceLabel = when (ownership) {
            DraftOwnership.GUI_OWNED -> "Draft"
            DraftOwnership.DETACHED -> "Independent draft"
            DraftOwnership.SCRIPT_IMPORTED -> "Read-only script request"
        }
        val capability =
            if (executionMode == RequestExecutionMode.FOLLOW_SCRIPT) "Execute the selected request in its complete script context" else when (mode) {
                GuiMode.GUI_READY -> "GUI execution supported"
                GuiMode.SEND_ONLY -> "Send from here; edit the source script to make changes"
                GuiMode.SCRIPT_ONLY -> "Run this request from its source script"
            }
        val details = listOf(sourceLabel, capability, if (dirty) "Unsaved changes" else "No pending changes")
        modeLabel.icon =
            if (executionMode == RequestExecutionMode.FOLLOW_SCRIPT) AllIcons.General.Information else when (mode) {
                GuiMode.GUI_READY -> ColorIcon(16, 16, 8, 8, KttpUiColors.success, false, 8)
                GuiMode.SEND_ONLY -> AllIcons.General.Information
                GuiMode.SCRIPT_ONLY -> AllIcons.General.Warning
            }
        modeLabel.toolTipText = details.joinToString("<br>", "<html>", "</html>")
        modeLabel.accessibleContext.accessibleDescription = details.joinToString(". ")
    }

    fun updateActionState(
        editable: Boolean,
        mode: GuiMode,
        ownership: DraftOwnership,
        linkedScriptPath: String?,
        hasGeneratedScript: Boolean
    ) {
        saveDraftButton.isEnabled = editable
        generateScriptButton.isEnabled = editable
        generateScriptButton.text = if (hasGeneratedScript && editable) "Update Script" else "Generate Script"
        detachButton.isEnabled = ownership == DraftOwnership.SCRIPT_IMPORTED && mode != GuiMode.SCRIPT_ONLY
        openScriptButton.isEnabled = linkedScriptPath != null

        generateScriptButton.toolTipText = if (editable) {
            if (hasGeneratedScript) {
                "Update the script previously generated by this draft."
            } else {
                "Generate a new .kttp.kts script from this draft."
            }
        } else {
            "Detach this imported script projection before generating a managed script."
        }
        detachButton.toolTipText = when {
            ownership != DraftOwnership.SCRIPT_IMPORTED -> "Detach is only available for imported script requests."
            mode == GuiMode.SCRIPT_ONLY -> "This request has unsupported script semantics. Run or edit its source script; it cannot be detached safely."
            else -> "Create an editable detached draft from this imported script projection."
        }
        openScriptButton.toolTipText = linkedScriptPath ?: "No linked script"
        sendButton.toolTipText = when (mode) {
            GuiMode.GUI_READY -> "Send the current draft directly over HTTP."
            GuiMode.SEND_ONLY -> "Send the projected HTTP request without touching the source script."
            GuiMode.SCRIPT_ONLY -> "This request cannot be executed safely from GUI."
        }
    }

    fun setEditable(editable: Boolean) {
        titleField.isEditable = editable
        requestNameField.isEditable = editable
        methodComboBox.isEnabled = editable
        urlField.isEditable = editable
        noRedirectCheck.isEnabled = editable
        noCookiesCheck.isEnabled = editable
        http2Check.isEnabled = editable
    }

    private fun createRowPanel(): JPanel {
        return JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(3))).apply {
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
        }
    }

    private fun styleField(field: JTextField, columns: Int) {
        field.columns = columns
        field.margin = JBUI.insets(6, 8)
    }

    private fun styleTransportToggle(checkBox: JCheckBox) {
        checkBox.isOpaque = false
    }
}
