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
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.JTabbedPane

/**
 * 工具窗口主面板：Collections 侧边栏 + 主内容区（请求编辑器）+ 下方结果标签页。
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val tabbedPane = JTabbedPane()

    val responseTabPanel = ResponseTabPanel(project)
    private val historyTabPanel = HistoryTabPanel(project)
    private val assertionsTabPanel = AssertionsTabPanel(project)

    // Collections 侧边栏（请求选中 → 加载到编辑器；脚本变更 → 若为当前来源则刷新编辑器）
    private lateinit var collectionsSidebar: CollectionsSidebar

    // 主内容区（请求编辑器）；收到响应时更新 Response / History / Assertions
    private lateinit var mainContentPanel: MainContentPanel

    init {
        // 先创建 mainContentPanel（不设置 onScriptFileChanged，避免循环引用）
        mainContentPanel = MainContentPanel(
            project,
            responseTabPanel,
            historyTabPanel,
            assertionsTabPanel,
            onShowResponse = { tabbedPane.selectedIndex = 0 },
            onScriptFileChanged = null,
            onDraftsChanged = null
        )

        // 再创建 collectionsSidebar（可以引用 mainContentPanel）
        collectionsSidebar = CollectionsSidebar(
            project,
            onRequestSelected = { declaration, sourceFile, index, focusBridge ->
                mainContentPanel.loadRequest(declaration, sourceFile, index, focusBridge)
            },
            onDraftSelected = { draft ->
                mainContentPanel.loadDraft(draft)
            },
            onScriptFileChanged = { file ->
                mainContentPanel.onScriptFileChanged(file)
            },
            onAddRequestToScript = { file, onAdded ->
                mainContentPanel.addCurrentRequestToScript(file, onAdded)
            }
        )

        // 最后设置 mainContentPanel 的 onScriptFileChanged（可以引用 collectionsSidebar）
        mainContentPanel.setScriptFileChangedCallback { file ->
            collectionsSidebar.notifyScriptFileChanged(file)
        }
        mainContentPanel.setDraftsChangedCallback {
            collectionsSidebar.refreshCollections()
        }

        // 右侧：请求编辑器和可调高度的结果标签页
        val rightPanel = JPanel(BorderLayout())

        installResultTabs(tabbedPane, responseTabPanel, historyTabPanel, assertionsTabPanel)
        val contentWithTabs = WorkspaceUi.split(
            true, "Kttp.RequestResponse.Proportion", 0.64f,
            mainContentPanel, WorkspaceUi.surface(tabbedPane)
        )
        rightPanel.add(contentWithTabs, BorderLayout.CENTER)
        border = JBUI.Borders.empty(8)
        preferredSize = Dimension(JBUI.scale(1120), JBUI.scale(760))
        add(WorkspaceUi.split(
            false, "Kttp.Navigation.Proportion", 0.22f,
            collectionsSidebar, rightPanel, initialFirstWidth = 300
        ), BorderLayout.CENTER)

        showEmptyState()
    }

    fun showEmptyState() {
        responseTabPanel.showEmpty("No requests executed yet.")
    }

    /**
     * 刷新侧边栏下方 Responses 里 Project/Kttp 分组（Scratches/Kttp/responses 变化时调用）。
     */
    fun refreshResponseCollections() {
        collectionsSidebar.refreshResponseCollections()
        historyTabPanel.reload()
    }

    /**
     * Defines the tool-window ID and shared result-tab and response-tree helpers.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    companion object {
        private const val KTPP_TOOL_WINDOW_ID = "Kttp"

        internal fun installResultTabs(
            tabbedPane: JTabbedPane,
            responseTabPanel: JPanel,
            historyTabPanel: JPanel,
            assertionsTabPanel: JPanel
        ) {
            tabbedPane.addTab("Response", responseTabPanel)
            tabbedPane.addTab("History", historyTabPanel)
            tabbedPane.addTab("Assertions", assertionsTabPanel)
        }

        /**
         * 若 Kttp 工具窗口已显示，则刷新其 Responses 树（Run 结束或 Send 成功后调用）。
         */
        @JvmStatic
        fun refreshResponseTreeIfVisible(project: Project) {
            val tw = ToolWindowManager.getInstance(project).getToolWindow(KTPP_TOOL_WINDOW_ID) ?: return
            val content = tw.contentManager.contents.firstOrNull() ?: return
            (content.component as? KttpToolWindowPanel)?.refreshResponseCollections()
        }
    }
}
