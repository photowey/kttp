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

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import io.github.photowey.kttp.idea.viewer.ResponseViewer
import java.awt.BorderLayout
import javax.swing.JTextArea
import javax.swing.JPanel

/**
 * Assertions 标签页：显示最近一次执行的断言结果。
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class AssertionsTabPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val assertionsArea = JTextArea(8, 40).apply {
        isEditable = false
    }

    init {
        add(JBScrollPane(assertionsArea), BorderLayout.CENTER)
        showEmpty("No assertions executed yet.")
    }

    fun showEmpty(message: String) {
        assertionsArea.text = message
    }

    fun showAssertions(
        assertionsPassed: Boolean,
        assertionResults: List<ResponseViewer.AssertionRow>,
        executionMessage: String? = null
    ) {
        if (assertionResults.isEmpty()) {
            assertionsArea.text = executionMessage ?: "No assertions were reported."
            return
        }

        assertionsArea.text = buildString {
            append(if (assertionsPassed) "PASSED" else "FAILED")
            append(" (${assertionResults.size} assertions)")
            appendLine()
            appendLine()
            assertionResults.forEach { result ->
                append(if (result.passed) "PASS" else "FAIL")
                append(" ")
                append(result.path)
                append(": ")
                append(result.message)
                appendLine()
            }
        }
    }
}
