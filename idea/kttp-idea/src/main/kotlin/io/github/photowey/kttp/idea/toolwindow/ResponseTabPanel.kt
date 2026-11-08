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
import io.github.photowey.kttp.idea.viewer.ResponseViewer
import io.github.photowey.kttp.idea.execution.RequestExecutionMode
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Response 标签页：空状态或 ResponseViewer（状态码/headers/body/断言 + Open/Copy Path/Reveal）。
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class ResponseTabPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val emptyLabel = JLabel("", SwingConstants.CENTER)
    private val emptyPanel = JPanel(BorderLayout()).apply {
        add(emptyLabel, BorderLayout.CENTER)
    }
    private val viewer = ResponseViewer(project)
    private val cardLayout = CardLayout()
    private val cards = JPanel(cardLayout).apply {
        add(emptyPanel, "empty")
        add(viewer, "viewer")
    }

    init {
        add(cards, BorderLayout.CENTER)
    }

    private fun showCard(name: String) {
        cardLayout.show(cards, name)
    }

    fun showEmpty(message: String) {
        emptyLabel.text = message
        showCard("empty")
    }

    fun showResponse(
        statusCode: Int,
        headers: Map<String, String>,
        body: String?,
        assertionsPassed: Boolean = true,
        assertionResults: List<ResponseViewer.AssertionRow> = emptyList(),
        responseFilePath: String? = null,
        bodyTruncated: Boolean = false,
        responseSize: Long? = null,
        executionMode: RequestExecutionMode? = null,
        totalTimeMs: Long? = null,
        httpTimeMs: Long? = null,
        executionFailure: String? = null,
        executionPending: Boolean = false
    ) {
        viewer.setResponse(
            statusCode = statusCode,
            headers = headers,
            body = body,
            assertionsPassed = assertionsPassed,
            assertionResults = assertionResults,
            responseFilePath = responseFilePath,
            bodyTruncated = bodyTruncated,
            responseSize = responseSize,
            executionMessage = executionFailure,
            executionMode = executionMode,
            totalTimeMs = totalTimeMs,
            httpTimeMs = httpTimeMs,
            executionPending = executionPending
        )
        showCard("viewer")
    }
}
