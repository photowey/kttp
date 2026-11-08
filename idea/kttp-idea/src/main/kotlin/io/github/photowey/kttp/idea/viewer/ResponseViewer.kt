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

import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBScrollPane
import java.awt.datatransfer.StringSelection
import com.intellij.openapi.vfs.LocalFileSystem
import java.awt.BorderLayout
import io.github.photowey.kttp.idea.execution.RequestExecutionMode
import java.io.File
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.JTextArea

/**
 * Displays theme-aware status, headers, a bounded body preview, and reported assertion results.
 * Provides actions for the associated response file and uses an IDEA JSON editor for body presentation.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class ResponseViewer(private val project: Project) : JPanel(BorderLayout()) {

    private val header = ResponseHeaderPanel(::openInEditor, ::copyPath, ::revealInFileManager)
    private val headersArea = JTextArea(4, 40).apply {
        isEditable = false
    }
    private val bodyDocument = EditorFactory.getInstance().createDocument("")

    // Body 使用 JsonFileType，平台 JSON 语言已提供 FoldingBuilder，大 JSON 可展开/折叠（遵循 IDE 设置：Settings → Editor → General → Code Folding）
    private val bodyEditorField =
        EditorTextField(bodyDocument, project, com.intellij.json.JsonFileType.INSTANCE).apply {
            isViewer = true
            setOneLineMode(false)
        }
    private val assertionsArea = JTextArea(6, 40).apply {
        isEditable = false
    }

    private var currentResponseFilePath: String? = null

    init {
        val tabs = JTabbedPane()
        tabs.addTab("Headers", JBScrollPane(headersArea))
        tabs.addTab("Body", JBScrollPane(bodyEditorField))
        tabs.addTab("Assertions", JBScrollPane(assertionsArea))
        add(header, BorderLayout.NORTH)
        add(tabs, BorderLayout.CENTER)
    }

    fun setResponse(
        statusCode: Int,
        headers: Map<String, String>,
        body: String?,
        assertionsPassed: Boolean,
        assertionResults: List<AssertionRow>,
        responseFilePath: String?,
        bodyTruncated: Boolean = false,
        responseSize: Long? = null,
        executionMessage: String? = null,
        executionMode: RequestExecutionMode? = null,
        totalTimeMs: Long? = null,
        httpTimeMs: Long? = null,
        executionPending: Boolean = false
    ) {
        currentResponseFilePath = responseFilePath
        val truncated = bodyTruncated || (body?.length ?: 0) > MAX_DISPLAY_CHARACTERS
        header.update(statusCode, executionMode, totalTimeMs, httpTimeMs, responseSize, truncated,
            executionMessage, responseFilePath, executionPending)
        headersArea.text = headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }.ifEmpty { "(no headers)" }
        val bodyText = body?.take(MAX_DISPLAY_CHARACTERS)?.let { formatJsonIfPossible(it) } ?: "(empty body)"
        WriteCommandAction.runWriteCommandAction(project) {
            bodyDocument.replaceString(0, bodyDocument.textLength, bodyText)
        }
        assertionsArea.text = if (assertionResults.isEmpty()) {
            if (executionPending) "Execution is still running; assertion results are not final."
            else executionMessage ?: "No assertions were reported."
        } else buildString {
            append(if (assertionsPassed) "PASSED" else "FAILED")
            append(" (${assertionResults.size} assertions)\n\n")
            assertionResults.forEach { r ->
                append(if (r.passed) "✓ " else "✗ ")
                append("${r.path}: ${r.message}\n")
            }
        }
    }

    private fun formatJsonIfPossible(raw: String): String {
        return try {
            val trimmed = raw.trim()
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                com.fasterxml.jackson.databind.ObjectMapper().readTree(trimmed).toPrettyString()
            } else raw
        } catch (_: Exception) {
            raw
        }
    }

    private fun openInEditor() {
        val path = currentResponseFilePath ?: return
        val application = ApplicationManager.getApplication()
        application.executeOnPooledThread {
            if (project.isDisposed || !File(path).isFile) return@executeOnPooledThread
            val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path) ?: return@executeOnPooledThread
            application.invokeLater({
                if (!project.isDisposed && file.isValid) WriteIntentReadAction.run<RuntimeException> {
                    com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(file, true)
                }
            }, project.disposed)
        }
    }

    private fun copyPath() {
        val path = currentResponseFilePath ?: return
        CopyPasteManager.getInstance().setContents(StringSelection(path))
    }

    private fun revealInFileManager() {
        val path = currentResponseFilePath ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            val file = File(path)
            if (!project.isDisposed && file.exists()) RevealFileAction.openFile(file)
        }
    }

    /**
     * Defines the maximum response text preview shown in the viewer.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    private companion object {
        const val MAX_DISPLAY_CHARACTERS = 50_000
    }

    /**
     * Represents an assertion result displayed in the response viewer.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    data class AssertionRow(val path: String, val passed: Boolean, val message: String)
}
