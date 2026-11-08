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

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import io.github.photowey.kttp.idea.icons.KttpIcons
import io.github.photowey.kttp.idea.lang.KttpFileType
import io.github.photowey.kttp.idea.path.KttpConsolesPath

/**
 * 在 Scratches and Consoles → Consoles → Kttp 下创建新的 .kttp.kts 脚本。
 * 用户通过 Scratches and Consoles 的 New → Kttp Script 触发，输入名称（如 hello）后创建 hello.kttp.kts。
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class NewKttpScriptAction : AnAction(
    "Kttp Script",
    "Create a new Kttp HTTP request script in Consoles/Kttp",
    KttpIcons.File
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val name = Messages.showInputDialog(
            project,
            "File name (extension .kttp.kts will be added if missing):",
            "New Kttp Script",
            Messages.getQuestionIcon(),
            "request",
            null
        ) ?: return
        val baseName = name.trim().ifEmpty { "request" }
        val fileName =
            if (baseName.endsWith("." + KttpFileType.INSTANCE.defaultExtension)) baseName else "${baseName}.${KttpFileType.INSTANCE.defaultExtension}"
        val templateContent = loadTemplateContent(project) ?: defaultTemplateContent()
        val kttpDir = KttpConsolesPath.getKttpConsolesDir()
        runWriteAction {
            val vfs = LocalFileSystem.getInstance()
            val dirVf = vfs.refreshAndFindFileByIoFile(kttpDir) ?: vfs.findFileByIoFile(kttpDir)
            if (dirVf == null) {
                Messages.showErrorDialog(project, "Could not resolve Kttp console directory: ${kttpDir.path}", "Error")
                return@runWriteAction
            }
            val file = dirVf.createChildData(this, fileName)
            VfsUtil.saveText(file, templateContent)
            FileEditorManager.getInstance(project).openFile(file, true)
        }
        // 刷新 consoles 父目录，使 Scratches and Consoles 视图能显示 Kttp 节点
        val consolesDir = kttpDir.parentFile
        if (consolesDir.exists()) {
            val consolesVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(consolesDir)
            if (consolesVf != null) VfsUtil.markDirtyAndRefresh(false, true, true, consolesVf)
        }
    }

    /**
     * Loads an internal IDEA template, falling back to the bundled resource.
     * The caller supplies default content when neither source is available.
     */
    private fun loadTemplateContent(project: Project): String? {
        // Try IntelliJ file template system first
        try {
            val templateManager = FileTemplateManager.getInstance(project)
            val template = templateManager.getInternalTemplate(TEMPLATE_NAME)
            return template.text
        } catch (e: Exception) {
            // Template not found, fall through to resource file
        }

        // Fallback to resource file
        return javaClass.getResourceAsStream(TEMPLATE_PATH)?.reader(Charsets.UTF_8)?.use { it.readText() }
    }

    private fun defaultTemplateContent(): String = """
        // Basic GET request with then block
        GET("https://httpbin.org/get") {
            header("Content-Type", "application/json")
            query("key", "value")
        }.then {
            assertStatus(200)
            val url: String = decode("$.url")
        }

        // POST request with JSON body
        val jsonBody = "{\"greeting\": \"Hello World!\"}"

        POST("https://httpbin.org/post") {
            header("Content-Type", "application/json")
            body(jsonBody)
        }.then {
            assertStatus(200)
            val greeting: String = decode("$.json.greeting")
            assertThat(greeting).isEqualTo("Hello World!")
        }

        // Using environment variables
        // val baseUrl: String by env
        // val apiKey: String by env

        // Using localFile to load request body
        // val requestBody: String by localFile("request.json")
    """.trimIndent()

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /**
     * Defines the internal template name and bundled template resource path.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    companion object {
        /**
         * Template name registered in plugin.xml as internalFileTemplate.
         * This name is used by FileTemplateManager.getInternalTemplate().
         */
        private const val TEMPLATE_NAME = "io.github.photowey.kttp.script.Kttp.kttp"

        /**
         * Fallback resource path for template file (if file template system not available).
         */
        private const val TEMPLATE_PATH = "/content/kttp/io.github.photowey.kttp.script.Kttp.tmpl"
    }
}
