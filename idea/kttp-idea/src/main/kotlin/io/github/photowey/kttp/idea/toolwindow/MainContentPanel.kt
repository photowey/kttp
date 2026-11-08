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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import io.github.photowey.kttp.idea.gui.GuiDraft
import io.github.photowey.kttp.idea.execution.KttpExecutionResult
import io.github.photowey.kttp.idea.script.KttpRequestDeclaration
import io.github.photowey.kttp.idea.viewer.ResponseViewer
import java.awt.BorderLayout
import java.io.IOException
import com.intellij.openapi.ui.Messages
import javax.swing.JPanel
import com.intellij.openapi.vfs.VirtualFile

/**
 * 主内容区：始终显示请求编辑器；收到响应时更新下方结果标签页并选中 Response 标签。
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/21
 */
class MainContentPanel(
    private val project: Project,
    private val responseTabPanel: ResponseTabPanel,
    private val historyTabPanel: HistoryTabPanel,
    private val assertionsTabPanel: AssertionsTabPanel,
    private val onShowResponse: () -> Unit = {},
    private val onScriptFileChanged: ((VirtualFile) -> Unit)? = null,
    private val onDraftsChanged: (() -> Unit)? = null
) : JPanel(BorderLayout()) {

    private val requestEditorPanel = RequestEditorPanel(project) { result, scriptFilePath ->
        showExecutionResult(result, scriptFilePath)
    }

    init {
        requestEditorPanel.onResponsePreview = { result ->
            responseTabPanel.showResponse(
                statusCode = requireNotNull(result.statusCode), headers = result.headers, body = result.body,
                responseFilePath = result.responseFilePath, bodyTruncated = result.bodyTruncated,
                responseSize = result.responseSize, executionMode = result.executionMode, httpTimeMs = result.httpTimeMs,
                executionPending = true
            )
            onShowResponse()
        }
        add(requestEditorPanel, BorderLayout.CENTER)
        // 设置 UI 同步回脚本后的回调，通知侧边栏刷新
        if (onScriptFileChanged != null) {
            requestEditorPanel.onScriptFileChanged = { file ->
                onScriptFileChanged.invoke(file)
            }
        }
        if (onDraftsChanged != null) {
            requestEditorPanel.onDraftsChanged = onDraftsChanged
        }
    }

    /**
     * 延迟设置脚本文件变更回调（用于避免循环引用）
     */
    fun setScriptFileChangedCallback(callback: (VirtualFile) -> Unit) {
        requestEditorPanel.onScriptFileChanged = callback
    }

    fun setDraftsChangedCallback(callback: () -> Unit) {
        requestEditorPanel.onDraftsChanged = callback
    }

    fun showResponse(
        statusCode: Int,
        headers: Map<String, String>,
        body: String?,
        assertionsPassed: Boolean,
        assertionResults: List<ResponseViewer.AssertionRow>,
        responseFilePath: String?
    ) {
        responseTabPanel.showResponse(
            statusCode = statusCode,
            headers = headers,
            body = body,
            assertionsPassed = assertionsPassed,
            assertionResults = assertionResults,
            responseFilePath = responseFilePath
        )
        onShowResponse()
    }

    private fun showExecutionResult(
        result: KttpExecutionResult,
        scriptFilePath: String?
    ) {
        val assertionRows = result.assertionResults.map {
            ResponseViewer.AssertionRow(it.path, it.passed, it.message)
        }

        if (result.statusCode != null) {
            responseTabPanel.showResponse(
                statusCode = result.statusCode,
                headers = result.headers,
                body = result.body,
                assertionsPassed = result.assertionsPassed,
                assertionResults = assertionRows,
                responseFilePath = result.responseFilePath,
                bodyTruncated = result.bodyTruncated,
                responseSize = result.responseSize,
                executionMode = result.executionMode,
                totalTimeMs = result.totalTimeMs,
                httpTimeMs = result.httpTimeMs,
                executionFailure = result.message.takeUnless { result.success }
            )
            result.responseFilePath?.let { responsePath ->
                try {
                    historyTabPanel.addEntry(
                        result.scriptName, responsePath, scriptFilePath,
                        result.executionMode, result.totalTimeMs, result.httpTimeMs
                    )
                } catch (failure: IOException) {
                    Messages.showWarningDialog(
                        project, failure.message ?: "The response was saved but history could not be updated.",
                        "Cannot Save Kttp History"
                    )
                }
            }
        } else {
            responseTabPanel.showResponse(
                statusCode = 0,
                headers = emptyMap(),
                body = result.message.ifBlank { "Request failed" },
                assertionsPassed = false,
                assertionResults = emptyList(),
                responseFilePath = null,
                executionMode = result.executionMode,
                totalTimeMs = result.totalTimeMs,
                httpTimeMs = result.httpTimeMs,
                executionFailure = result.message.takeUnless { result.success }
            )
        }

        assertionsTabPanel.showAssertions(
            assertionsPassed = result.assertionsPassed,
            assertionResults = assertionRows,
            executionMessage = if (result.success) "No assertions were reported." else result.message
        )
        onShowResponse()
    }

    /**
     * 加载请求到 GUI 编辑器；sourceFile 与 declarationIndex 用于脚本变更时联动刷新
     */
    fun loadRequest(
        declaration: KttpRequestDeclaration?,
        sourceFile: VirtualFile? = null,
        declarationIndex: Int? = null,
        focusBridge: Boolean = true
    ) {
        requestEditorPanel.loadFromRequest(declaration, sourceFile, declarationIndex, focusBridge)
    }

    fun loadDraft(draft: GuiDraft) {
        requestEditorPanel.loadDraft(draft)
    }

    fun addCurrentRequestToScript(targetScript: VirtualFile?): VirtualFile? =
        requestEditorPanel.addCurrentRequestToScript(targetScript)

    /**
     * 脚本文件变更时调用：若当前编辑器正在显示该文件中的请求，则重新解析并刷新表单（脚本 ↔ UI 联动）
     */
    fun onScriptFileChanged(file: VirtualFile) {
        if (file.path != requestEditorPanel.currentSourceFile?.path) return
        val idx = requestEditorPanel.currentDeclarationIndex ?: return
        val revision = requestEditorPanel.selectionRevision
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed || !file.isValid ||
                file.path != requestEditorPanel.currentSourceFile?.path ||
                idx != requestEditorPanel.currentDeclarationIndex ||
                revision != requestEditorPanel.selectionRevision
            ) return@invokeLater
            requestEditorPanel.refreshCurrentSource()
        }
    }
}
