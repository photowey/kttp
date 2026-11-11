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
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import io.github.photowey.kttp.idea.execution.ExecutionSummary
import io.github.photowey.kttp.idea.execution.RequestExecutionMode
import io.github.photowey.kttp.idea.io.HistoryPersistence
import io.github.photowey.kttp.idea.io.HistoryPersistence.Record
import io.github.photowey.kttp.idea.path.KttpFileNavigation
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants

/**
 * History 标签页：显示脚本名、可用的执行模式与耗时，以及响应文件的简短路径；双击条目打开响应。
 * [HistoryPersistence] 统一存储记录到 Consoles/Kttp/responses/kttp-history.json，面板创建时加载并订阅更新。
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class HistoryTabPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val emptyLabel = JLabel("", SwingConstants.CENTER)
    private val listModel = DefaultListModel<HistoryEntry>()
    private val consolesRootPath = Path.of(PathManager.getConfigPath(), "consoles", "Kttp").toString()
    private val list = JList<HistoryEntry>(listModel).apply {
        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): java.awt.Component {
                val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                label.text = when (value) {
                    is HistoryEntry -> listOf(
                        value.scriptName,
                        ExecutionSummary.format(value.executionMode, value.totalTimeMs, value.httpTimeMs)
                    )
                        .filter { it.isNotBlank() }.joinToString(" · ") + " → ${
                        historyResponseDisplayPath(
                            value.responseFilePath,
                            project.basePath,
                            consolesRootPath
                        )
                    }"

                    else -> value?.toString() ?: ""
                }
                label.toolTipText = (value as? HistoryEntry)?.responseFilePath
                return label
            }
        }
    }
    private val listPanel = JPanel(BorderLayout()).apply {
        add(JScrollPane(list), BorderLayout.CENTER)
    }

    init {
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2) return
                val path = list.selectedValue?.responseFilePath ?: return
                KttpFileNavigation.open(project, path)
            }
        })
        add(emptyLabel, BorderLayout.CENTER)
        HistoryPersistence.subscribe(project) {
            val application = ApplicationManager.getApplication()
            if (application.isDispatchThread) {
                if (!project.isDisposed) reload()
            } else {
                application.invokeLater({ if (!project.isDisposed) reload() }, project.disposed)
            }
        }
        reload()
    }

    fun reload() {
        try {
            applySnapshot(HistoryPersistence.load())
        } catch (failure: IOException) {
            showPersistenceFailure(failure)
        }
    }

    fun showEmpty(message: String) {
        emptyLabel.text = message
        if (componentCount != 1 || getComponent(0) != emptyLabel) {
            removeAll()
            add(emptyLabel, BorderLayout.CENTER)
        }
        revalidate()
        repaint()
    }

    fun addEntry(
        scriptName: String, responseFilePath: String, scriptFilePath: String?,
        executionMode: RequestExecutionMode? = null, totalTimeMs: Long? = null, httpTimeMs: Long? = null
    ) {
        try {
            applySnapshot(
                HistoryPersistence.append(
                    Record(
                        scriptName,
                        responseFilePath,
                        scriptFilePath,
                        executionMode,
                        totalTimeMs,
                        httpTimeMs
                    )
                )
            )
        } catch (failure: IOException) {
            showPersistenceFailure(failure)
            throw failure
        }
    }

    fun clear() {
        try {
            applySnapshot(HistoryPersistence.clear())
            showEmpty("No history.")
        } catch (failure: IOException) {
            showPersistenceFailure(failure)
            throw failure
        }
    }

    fun getSelectedResponseFilePath(): String? =
        list.selectedValue?.responseFilePath

    fun getSelectedScriptName(): String? =
        list.selectedValue?.scriptName

    /** 返回选中记录关联的脚本文件路径；独立 GUI 请求可能没有关联脚本。 */
    fun getSelectedScriptFilePath(): String? =
        list.selectedValue?.scriptFilePath

    private fun applySnapshot(records: List<Record>) {
        val selectedPath = list.selectedValue?.responseFilePath
        listModel.clear()
        records.forEach { record ->
            listModel.addElement(
                HistoryEntry(
                    record.scriptName,
                    record.responseFilePath,
                    record.scriptFilePath,
                    record.executionMode,
                    record.totalTimeMs,
                    record.httpTimeMs
                )
            )
        }
        if (records.isEmpty()) {
            showEmpty("No history yet.")
            return
        }
        removeAll()
        add(listPanel, BorderLayout.CENTER)
        list.selectedIndex = records.indexOfFirst { it.responseFilePath == selectedPath }
        revalidate()
        repaint()
    }

    private fun showPersistenceFailure(failure: IOException) {
        val message = failure.message ?: "Cannot read or save history."
        if (listModel.isEmpty) {
            showEmpty(message)
        } else {
            removeAll()
            emptyLabel.text = message
            add(emptyLabel, BorderLayout.NORTH)
            add(listPanel, BorderLayout.CENTER)
            revalidate()
            repaint()
        }
    }

    /**
     * Carries the paths, execution mode, and timing values displayed for one history row.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    data class HistoryEntry(
        val scriptName: String,
        val responseFilePath: String,
        val scriptFilePath: String? = null,
        val executionMode: RequestExecutionMode? = null,
        val totalTimeMs: Long? = null,
        val httpTimeMs: Long? = null
    )
}

/** A display-only path; persisted entries and file operations retain the original absolute path. */
internal fun historyResponseDisplayPath(
    responseFilePath: String,
    projectBasePath: String?,
    consolesRootPath: String
): String {
    val response = try {
        Path.of(responseFilePath).normalize()
    } catch (_: InvalidPathException) {
        return responseFilePath.substringAfterLast('/').substringAfterLast('\\')
    }
    for (rootPath in listOfNotNull(consolesRootPath, projectBasePath)) {
        val root = try {
            Path.of(rootPath).normalize()
        } catch (_: InvalidPathException) {
            continue
        }
        if (response != root && response.startsWith(root)) {
            return root.relativize(response).toString().replace(File.separatorChar, '/')
        }
    }
    return response.fileName?.toString() ?: responseFilePath
}
