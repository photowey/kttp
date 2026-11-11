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

package io.github.photowey.kttp.idea.run

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkWithPopupMenuInfo
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import io.github.photowey.kttp.idea.path.KttpFileNavigation
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.nio.file.Path

/**
 * Links response paths in the Run console. The path opens the editor; its context menu reveals the file.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/18
 */
class KttpResponseFileLinkFilter(private val project: Project) : Filter {
    private var previousLine: String? = null

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val followsSavedResponse = previousLine?.trimEnd() == "Response file saved."
        previousLine = line
        if (!followsSavedResponse || !line.startsWith("> ")) return null
        val pathText = line.drop(2).trimEnd()
        if (pathText.isEmpty()) return null
        val filePath = pathText.removePrefix("file://")
        val start = entireLength - line.length + 2
        return Filter.Result(start, start + pathText.length, ResponseFileHyperlink(project, filePath))
    }
}

/**
 * Uses public console hyperlink and action APIs for opening and revealing a response file.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/11
 */
private class ResponseFileHyperlink(
    private val project: Project,
    private val filePath: String
) : HyperlinkWithPopupMenuInfo {
    override fun navigate(project: Project) = KttpFileNavigation.open(project, filePath)

    override fun getPopupMenuGroup(event: MouseEvent): ActionGroup = DefaultActionGroup(
        DumbAwareAction.create(RevealFileAction.getActionName()) {
            ApplicationManager.getApplication().executeOnPooledThread {
                val path = Path.of(filePath)
                if (!project.isDisposed && Files.isRegularFile(path)) RevealFileAction.openFile(path)
            }
        }
    )
}
