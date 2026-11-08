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

package io.github.photowey.kttp.idea.toolbar

import com.intellij.execution.RunManagerEx
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiManager
import io.github.photowey.kttp.idea.run.KttpRunConfiguration
import io.github.photowey.kttp.idea.run.KttpRunConfigurationType
import io.github.photowey.kttp.idea.run.ScriptRunStrategy
import io.github.photowey.kttp.idea.script.KttpScript
import io.github.photowey.kttp.idea.script.toKttpScript
import org.jetbrains.kotlin.psi.KtFile

private val LOG = Logger.getInstance(RunKttpFileAction::class.java)

/**
 * Toolbar action to run all requests in the current .kttp.kts file.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/17
 */
class RunKttpFileAction : AnAction(
    "Run All Requests",
    "Run all requests in the current .kttp.kts file",
    AllIcons.Actions.RunAll
) {

    /**
     * Defines the action identifier for running every request in a script file.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/17
     */
    companion object {
        const val ID = "kttp.run.file"
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /** Resolves the script from PSI_FILE first, then VIRTUAL_FILE. */
    private fun getKttpScript(e: AnActionEvent): KttpScript? {
        val psiFile = (e.getData(CommonDataKeys.PSI_FILE) as? KtFile)
            ?: (e.getData(CommonDataKeys.VIRTUAL_FILE)?.let { vf ->
                e.project?.let { PsiManager.getInstance(it).findFile(vf) as? KtFile }
            })
        return psiFile?.takeIf { it.name.endsWith(".kttp.kts") }?.toKttpScript()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = getKttpScript(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: run {
            LOG.warn("[RunKttpFileAction] project is null")
            return
        }
        val script = getKttpScript(e) ?: run {
            LOG.warn("[RunKttpFileAction] no KttpScript in context (PSI_FILE or VIRTUAL_FILE)")
            return
        }

        val configurationType = ConfigurationTypeUtil.findConfigurationType(KttpRunConfigurationType::class.java)
            ?: run {
                LOG.error("[RunKttpFileAction] KttpRunConfigurationType not found")
                return
            }
        val factory = configurationType.configurationFactories.firstOrNull()
            ?: run {
                LOG.error("[RunKttpFileAction] no configuration factory for KttpRunConfigurationType")
                return
            }

        val runManager = RunManagerEx.getInstanceEx(project)
        val name = "All in ${script.nameWithoutExtension}"
        val runnerAndConfigurationSettings = runManager.createConfiguration(name, factory)
        runnerAndConfigurationSettings.isTemporary = true
        runManager.addConfiguration(runnerAndConfigurationSettings)

        val runConfiguration = runnerAndConfigurationSettings.configuration as? KttpRunConfiguration
            ?: run {
                LOG.error("[RunKttpFileAction] failed to cast to KttpRunConfiguration, got: ${runnerAndConfigurationSettings.configuration?.javaClass}")
                return
            }

        val scriptPath = script.virtualFile?.path
        if (scriptPath == null) {
            LOG.error("[RunKttpFileAction] script.virtualFile?.path is null")
            return
        }

        runConfiguration.apply {
            this.scriptPath = scriptPath
            this.runStrategy = ScriptRunStrategy.WholeFile
            this.selectedEnvironment = null
        }

        LOG.info("[RunKttpFileAction] Running configuration: name=$name, scriptPath=$scriptPath, runStrategy=${runConfiguration.runStrategy}")

        try {
            ExecutionUtil.runConfiguration(runnerAndConfigurationSettings, DefaultRunExecutor.getRunExecutorInstance())
        } catch (ex: Exception) {
            LOG.error("[RunKttpFileAction] Failed to run configuration", ex)
        }
    }
}
