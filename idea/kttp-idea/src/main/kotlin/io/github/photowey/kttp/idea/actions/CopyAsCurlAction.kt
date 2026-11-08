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

package io.github.photowey.kttp.idea.actions

import com.intellij.execution.CantRunException
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import io.github.photowey.kttp.idea.cli.KttpCliInvocation
import io.github.photowey.kttp.idea.env.KttpEnvironmentService
import io.github.photowey.kttp.idea.lang.KttpFileType
import io.github.photowey.kttp.idea.run.kttpRunScriptJavaParameters
import io.github.photowey.kttp.idea.script.KttpScript
import io.github.photowey.kttp.idea.script.toKttpScript
import java.awt.datatransfer.StringSelection
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Copies the current request as a cURL command to the clipboard.
 * Evaluates the script through the CLI's CURL mode to resolve the selected request's
 * body, query, headers, environment values, and local files. Top-level script code
 * and delegated request dependencies can still execute during evaluation.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/16
 */
class CopyAsCurlAction : AnAction(
    "Copy as cURL",
    "Copy request as cURL command",
    com.intellij.icons.AllIcons.Actions.Copy
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        if (!isKttpFile(file)) {
            showNotification(project, "Not a Kttp script file", NotificationType.WARNING)
            return
        }

        if (PsiManager.getInstance(project).findFile(file) == null) {
            showNotification(project, "Could not resolve script file", NotificationType.ERROR)
            return
        }

        val script = file.toKttpScript(project) ?: run {
            showNotification(project, "Not a valid Kttp script", NotificationType.WARNING)
            return
        }

        val caretOffset = e.getData(CommonDataKeys.EDITOR)?.caretModel?.offset ?: -1
        val requestNumber = findRequestNumber(project, script, caretOffset) ?: return

        val scriptPath = Paths.get(file.path)
        if (!scriptPath.toFile().exists()) {
            showNotification(project, "Script file not found", NotificationType.ERROR)
            return
        }

        generateCurl(project, file, scriptPath, requestNumber)
    }

    private fun findRequestNumber(project: Project, script: KttpScript, caretOffset: Int): Int? {
        val declarations = script.findRequestDeclarations()
        val declaration = declarations.firstOrNull { it.element?.textRange?.contains(caretOffset) == true }
            ?: declarations.firstOrNull()
        if (declaration == null) {
            showNotification(project, "No HTTP request found in script", NotificationType.WARNING)
            return null
        }
        val number = script.getRequestDeclarationNumber(declaration)
        if (number >= 1) return number
        showNotification(project, "Could not determine request index", NotificationType.WARNING)
        return null
    }

    private fun generateCurl(project: Project, file: VirtualFile, scriptPath: Path, requestNumber: Int) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating cURL...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                try {
                    val invocation = curlInvocation(project, file, scriptPath, requestNumber)
                    val commandLine = kttpRunScriptJavaParameters(project, invocation).toCommandLine()
                    val curlCommand = CurlCliRunner().run(commandLine, indicator)
                    publishCurl(project, indicator, curlCommand)
                } catch (ex: ProcessCanceledException) {
                    throw ex
                } catch (ex: CantRunException) {
                    notifyLater(project, ex.message ?: "Cannot run Kttp CLI", NotificationType.ERROR)
                } catch (ex: Exception) {
                    notifyLater(project, "Failed to get cURL: ${ex.message ?: ex}", NotificationType.ERROR)
                }
            }
        })
    }

    private fun curlInvocation(project: Project, file: VirtualFile, scriptPath: Path, requestNumber: Int) =
        KttpCliInvocation(
            scriptPath = scriptPath.toAbsolutePath().toString(),
            requestNumber = requestNumber - 1,
            envVariables = KttpEnvironmentService.getInstance(project).getEnvironmentVariables(file),
            baseDir = project.basePath,
            executionMode = "CURL"
        )

    private fun publishCurl(project: Project, indicator: ProgressIndicator, curlCommand: String) {
        indicator.checkCanceled()
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed || indicator.isCanceled) return@invokeLater
            if (curlCommand.isBlank()) {
                showNotification(
                    project,
                    "No cURL output (request may have failed to evaluate)",
                    NotificationType.WARNING
                )
            } else {
                CopyPasteManager.getInstance().setContents(StringSelection(curlCommand))
                showNotification(project, "cURL command copied to clipboard", NotificationType.INFORMATION)
            }
        }
    }

    private fun notifyLater(project: Project, message: String, type: NotificationType) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) showNotification(project, message, type)
        }
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null && isKttpFile(file)
    }

    private fun isKttpFile(file: VirtualFile): Boolean {
        return file.name.endsWith(".${KttpFileType.INSTANCE.defaultExtension}")
    }

    private fun showNotification(project: Project, message: String, type: NotificationType) {
        val title = when (type) {
            NotificationType.ERROR -> "Error"
            NotificationType.WARNING -> "Warning"
            else -> "Kttp"
        }
        Notifications.Bus.notify(
            Notification("Kttp", title, message, type),
            project
        )
    }
}
