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

package io.github.photowey.kttp.idea.env

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import io.github.photowey.kttp.idea.lang.KttpFileType
import java.io.File
import javax.swing.JComponent

/**
 * Environment selector action for Kttp toolbar.
 * Shows dropdown with available environments from kttp.env.json.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/16
 */
class ChooseEnvironmentAction : ComboBoxAction() {

    init {
        templatePresentation.text = "Environment"
        templatePresentation.description = "Select environment for script execution"
        // Use a dropdown-like icon
        templatePresentation.icon = AllIcons.Nodes.Parameter
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = getKttpFile(e)

        val isVisible = project != null && file != null
        e.presentation.isEnabledAndVisible = isVisible

        if (isVisible) {
            val service = KttpEnvironmentService.getInstance(project)
            val selected = service.selectedEnvironment
            // Show selected environment name or default text
            e.presentation.text = if (selected != null) "Environment: $selected" else "Select Environment Before Run"
        }
    }

    override fun createPopupActionGroup(
        button: JComponent,
        dataContext: DataContext
    ): DefaultActionGroup {
        val project = dataContext.getData(CommonDataKeys.PROJECT)
            ?: return DefaultActionGroup()

        // Try to get file from data context first, then from selected editor
        val file = dataContext.getData(CommonDataKeys.VIRTUAL_FILE)
            ?: FileEditorManager.getInstance(project).selectedEditor?.file

        return createEnvironmentActionGroup(project, file)
    }

    private fun createEnvironmentActionGroup(project: Project, file: VirtualFile?): DefaultActionGroup {
        val group = DefaultActionGroup()
        val service = KttpEnvironmentService.getInstance(project)
        val environments = service.getAvailableEnvironments(file)

        // <No Environment> option
        group.add(NoEnvironmentAction(service))
        group.addSeparator()

        // List available environments
        if (environments.isEmpty()) {
            // Show a disabled "No environments configured" item
            group.add(DisabledAction("No environments configured"))
        } else {
            environments.forEach { env ->
                group.add(SelectEnvironmentAction(service, env.name))
            }
        }

        group.addSeparator()

        // Edit Environment option
        group.add(EditEnvironmentAction(project, file))

        return group
    }

    private fun getKttpFile(e: AnActionEvent): VirtualFile? {
        return e.getData(CommonDataKeys.VIRTUAL_FILE)
            ?.takeIf { it.name.endsWith(".${KttpFileType.INSTANCE.defaultExtension}") }
    }

    /**
     * Disabled action (grayed out item)
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/16
     */
    private class DisabledAction(text: String) : AnAction(text) {
        init {
            templatePresentation.isEnabled = false
        }

        override fun actionPerformed(e: AnActionEvent) {
            // No-op
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = false
        }
    }

    /**
     * Action to select "No Environment" (clear selection).
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/16
     */
    private class NoEnvironmentAction(
        private val service: KttpEnvironmentService
    ) : AnAction("<No Environment>", null, AllIcons.Actions.Cancel) {
        override fun actionPerformed(e: AnActionEvent) {
            service.setSelectedEnvironment(null)
            // Refresh the editor notification panel to update the display
            refreshEditorNotifications(e.project)
        }
    }

    /**
     * Action to select a specific environment.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/16
     */
    private class SelectEnvironmentAction(
        private val service: KttpEnvironmentService,
        private val envName: String
    ) : AnAction(envName, null, AllIcons.Nodes.Parameter) {
        override fun actionPerformed(e: AnActionEvent) {
            service.setSelectedEnvironment(envName)
            refreshEditorNotifications(e.project)
        }
    }

    /**
     * Action to open/edit the kttp.env.json file.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/16
     */
    private class EditEnvironmentAction(
        private val project: Project,
        private val scriptFile: VirtualFile?
    ) : AnAction("Edit Environments...", null, AllIcons.Actions.Edit) {
        override fun actionPerformed(e: AnActionEvent) {
            val service = KttpEnvironmentService.getInstance(project)
            val envFile = service.findEnvFile(scriptFile)

            if (envFile != null) {
                FileEditorManager.getInstance(project).openFile(envFile, true)
            } else {
                createAndOpenEnvFile(project, scriptFile)
            }
        }

        private fun createAndOpenEnvFile(project: Project, scriptFile: VirtualFile?) {
            val content = """{
  "dev": {
    "baseUrl": "https://httpbin.org",
    "apiKey": "your-api-key-here"
  },
  "test": {
    "baseUrl": "https://httpbin.org",
    "apiKey": "test-api-key"
  }
}"""

            val directory = scriptFile?.parent
                ?: project.basePath?.let { LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(it)) }
                ?: return

            // Execute file creation in write action
            com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
                try {
                    val envFile = directory.createChildData(this, KttpEnvFileType.FILE_NAME)
                    envFile.setBinaryContent(content.toByteArray())
                    // Open file in EDT
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        FileEditorManager.getInstance(project).openFile(envFile, true)
                    }
                } catch (e: Exception) {
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        com.intellij.openapi.ui.Messages.showErrorDialog(
                            project,
                            "Failed to create ${KttpEnvFileType.FILE_NAME}: ${e.message}",
                            "Error"
                        )
                    }
                }
            }
        }
    }

    /**
     * Refreshes editor notifications after an environment selection changes.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/16
     */
    companion object {
        /**
         * Refresh editor notifications to update the environment display
         */
        private fun refreshEditorNotifications(project: Project?) {
            project?.let {
                com.intellij.ui.EditorNotifications.getInstance(it).updateAllNotifications()
            }
        }
    }
}
