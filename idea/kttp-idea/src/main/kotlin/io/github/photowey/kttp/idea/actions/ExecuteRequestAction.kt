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

import com.intellij.execution.RunManagerEx
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import io.github.photowey.kttp.core.context.execution.DeclarationCoordinates
import io.github.photowey.kttp.idea.env.KttpEnvironmentService
import io.github.photowey.kttp.idea.lang.KttpFileType
import io.github.photowey.kttp.idea.run.KttpRunConfiguration
import io.github.photowey.kttp.idea.run.KttpRunConfigurationType
import io.github.photowey.kttp.idea.run.ScriptRunStrategy

private val LOG = Logger.getInstance(ExecuteRequestAction::class.java)

/**
 * Runs the current script through a temporary run configuration and the platform Run tool window.
 * Request coordinates in the action context select one declaration; without them, runs the whole file.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class ExecuteRequestAction : AnAction(
    "Execute Kttp Request",
    "Execute the current kttp request",
    com.intellij.icons.AllIcons.Actions.Execute
) {

    /**
     * Defines the action ID and context keys used to select a script request.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    companion object {
        const val ID = "io.github.photowey.kttp.ExecuteRequest"

        // Data keys for passing request coordinates from gutter
        val REQUEST_NUMBER_KEY: DataKey<Int?> = DataKey.create("kttp.request.number")
        val REQUEST_NAME_KEY: DataKey<String?> = DataKey.create("kttp.request.name")
    }

    override fun actionPerformed(e: AnActionEvent) {
        LOG.info("[ExecuteRequestAction] actionPerformed called")
        val project = e.project ?: run {
            LOG.warn("[ExecuteRequestAction] project is null")
            return
        }

        val virtualFile = findScriptFile(e)

        if (virtualFile == null || !virtualFile.name.endsWith(".${KttpFileType.INSTANCE.defaultExtension}")) {
            LOG.warn("[ExecuteRequestAction] virtualFile is null or not a kttp file: ${virtualFile?.name}")
            Messages.showWarningDialog(project, "Please open a .kttp.kts file to execute.", "No Kttp Script")
            return
        }

        // Get request coordinate from data context (set by gutter click)
        val requestNumber = e.getData(REQUEST_NUMBER_KEY)
        val requestName = e.getData(REQUEST_NAME_KEY)

        LOG.info("[ExecuteRequestAction] executing file: ${virtualFile.path}, requestNumber=$requestNumber, requestName=$requestName")

        val envService = KttpEnvironmentService.getInstance(project)
        if (!hasRequiredEnvironment(project, virtualFile, envService)) return

        val runManager = RunManagerEx.getInstanceEx(project)
        val settings = createRunSettings(
            runManager, virtualFile, envService.getEnvironmentVariables(virtualFile), requestNumber, requestName
        )
        runManager.addConfiguration(settings)
        LOG.info("[ExecuteRequestAction] configuration added, executing...")
        ExecutionUtil.runConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
        LOG.info("[ExecuteRequestAction] ExecutionUtil.runConfiguration called")
    }

    private fun findScriptFile(e: AnActionEvent): VirtualFile? =
        e.getData(CommonDataKeys.VIRTUAL_FILE)
            ?: e.getData(CommonDataKeys.PSI_FILE)?.virtualFile
            ?: e.getData(CommonDataKeys.EDITOR)?.document?.let { doc ->
                FileDocumentManager.getInstance().getFile(doc)
            }

    private fun hasRequiredEnvironment(
        project: Project,
        file: VirtualFile,
        envService: KttpEnvironmentService
    ): Boolean {
        if (envService.selectedEnvironment != null || envService.getAvailableEnvironments(file).isEmpty()) return true
        Messages.showErrorDialog(
            project,
            "No environment selected. Please select an environment (e.g. dev/test) in the toolbar before running. Scripts that use `by env` require an environment to be selected.",
            "No Environment Selected"
        )
        return false
    }

    private fun createRunSettings(
        runManager: RunManagerEx,
        file: VirtualFile,
        envVariables: Map<String, String>,
        requestNumber: Int?,
        requestName: String?
    ): RunnerAndConfigurationSettings {
        val settings = runManager.createConfiguration(
            configurationName(file, requestNumber, requestName), KttpRunConfigurationType()
        )
        settings.isTemporary = true
        val configuration = settings.configuration as? KttpRunConfiguration
        if (configuration != null) {
            configuration.scriptPath = file.path
            configuration.environmentVariables = envVariables
            configuration.runStrategy = runStrategy(requestNumber, requestName)
            LOG.info("[ExecuteRequestAction] runStrategy: ${configuration.runStrategy}")
        }
        return settings
    }

    private fun configurationName(file: VirtualFile, requestNumber: Int?, requestName: String?): String {
        val baseName = file.name.removeSuffix(".${KttpFileType.INSTANCE.defaultExtension}")
        return when {
            requestNumber != null -> "$baseName: Request #$requestNumber"
            requestName != null -> "$baseName: $requestName"
            else -> baseName
        }
    }

    private fun runStrategy(requestNumber: Int?, requestName: String?): ScriptRunStrategy = when {
        requestNumber != null -> ScriptRunStrategy.SingleDeclaration(DeclarationCoordinates(requestNumber))
        requestName != null -> ScriptRunStrategy.SingleDeclaration(DeclarationCoordinates(requestName))
        else -> ScriptRunStrategy.WholeFile
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = findScriptFile(e)
        e.presentation.isEnabledAndVisible =
            project != null && file != null && file.name.endsWith(".${KttpFileType.INSTANCE.defaultExtension}")
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
