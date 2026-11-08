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

package io.github.photowey.kttp.idea.viewer

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.WrapLayout
import io.github.photowey.kttp.idea.execution.ExecutionSummary
import io.github.photowey.kttp.idea.execution.RequestExecutionMode
import io.github.photowey.kttp.idea.toolwindow.KttpUiColors
import io.github.photowey.kttp.idea.toolwindow.useBoldUiFont
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Groups response status, wrapping execution metadata and accessible file actions in one header.
 * HTTP status stays distinct from script or assertion failures after a successful exchange.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/07
 */
internal class ResponseHeaderPanel(open: () -> Unit, copy: () -> Unit, reveal: () -> Unit) : JPanel() {
    private val status = JLabel().apply { useBoldUiFont() }
    private val metadata = JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(3))).apply {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        toolTipText = ExecutionSummary.TIMING_DESCRIPTION
        accessibleContext.accessibleName = "Completed request execution"
    }
    private val failure = JBLabel().apply {
        setAllowAutoWrapping(true)
        foreground = KttpUiColors.error
        alignmentX = LEFT_ALIGNMENT
        border = JBUI.Borders.empty(2, 8, 6, 8)
        isVisible = false
    }
    private val fileActions = listOf(
        fileAction("Open in Editor", AllIcons.Actions.MenuOpen, open),
        fileAction("Copy Path", AllIcons.Actions.Copy, copy),
        fileAction("Reveal in File Manager", AllIcons.Nodes.Folder, reveal)
    )

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.empty(4, 0)
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
            border = JBUI.Borders.empty(0, 8)
            add(status, BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(2), 0)).apply {
                isOpaque = false
                fileActions.forEach { add(it) }
            }, BorderLayout.EAST)
        })
        add(metadata)
        add(failure)
    }

    fun update(
        statusCode: Int, mode: RequestExecutionMode?, totalTimeMs: Long?, httpTimeMs: Long?,
        responseSize: Long?, truncated: Boolean, message: String?, responseFilePath: String?, pending: Boolean
    ) {
        status.text = if (statusCode > 0) "HTTP $statusCode" else "Request failed"
        status.foreground = when (statusCode) {
            in 200..299 -> KttpUiColors.success
            in 300..399 -> KttpUiColors.info
            else -> KttpUiColors.error
        }
        metadata.removeAll()
        listOfNotNull(
            mode?.displayName, totalTimeMs?.let { "Total $it ms" }, httpTimeMs?.let { "HTTP $it ms" },
            responseSize?.let { "$it bytes" }, "Preview".takeIf { truncated },
            "Execution in progress".takeIf { pending }
        ).forEach { text ->
            metadata.add(JLabel(text).apply {
                foreground = UIUtil.getContextHelpForeground()
                toolTipText = if (text == "Preview") {
                    "The body preview is truncated. Open the saved response file for the complete content."
                } else if (pending && text == "Execution in progress") {
                    "The response is available; response saving or script processing is still running."
                } else ExecutionSummary.TIMING_DESCRIPTION
            })
        }
        metadata.isVisible = metadata.componentCount > 0
        failure.text = message?.let { "<html>Failed: ${StringUtil.escapeXmlEntities(it)}</html>" }
        failure.toolTipText = message
        failure.isVisible = !message.isNullOrBlank()
        fileActions.forEach {
            it.isEnabled = !responseFilePath.isNullOrBlank()
            it.toolTipText = it.accessibleContext.accessibleName +
                (responseFilePath?.let { path -> " — $path" } ?: " — no saved response file")
        }
        revalidate()
        repaint()
    }

    override fun getPreferredSize(): Dimension {
        val owner = parent
        val available = if (owner != null && owner.width > 0) owner.width - owner.insets.left - owner.insets.right else width
        if (available > 0) {
            metadata.setSize((available - insets.left - insets.right).coerceAtLeast(0), metadata.height)
            failure.setSize((available - insets.left - insets.right).coerceAtLeast(0), failure.height)
            (layout as? BoxLayout)?.invalidateLayout(this)
        }
        return super.getPreferredSize()
    }

    private fun fileAction(name: String, actionIcon: Icon, action: () -> Unit): JButton = JButton(actionIcon).apply {
        accessibleContext.accessibleName = name
        toolTipText = name
        putClientProperty("JButton.buttonType", "square")
        addActionListener { action() }
    }
}
