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

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import io.github.photowey.kttp.idea.gui.GuiDraft
import io.github.photowey.kttp.idea.gui.GuiScriptGenerator
import io.github.photowey.kttp.idea.path.KttpConsolesPath
import io.github.photowey.kttp.idea.path.KttpFileNavigation
import io.github.photowey.kttp.idea.path.KttpProjectPath
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Script document writes and conflict protection, called from the write-safe UI queue.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal class RequestScriptFileCoordinator(private val project: Project) {
    /**
     * Appends to the selected script document or creates a unique project-local temporary script.
     * Existing text and unsaved document edits are retained; document writes use an IDEA write command.
     */
    fun append(draft: GuiDraft, targetScript: VirtualFile?): VirtualFile {
        require(targetScript == null || targetScript.isValid && targetScript.name.endsWith(".kttp.kts") && targetScript.isWritable) {
            "The selected script is unavailable or read-only."
        }
        val targetFile = targetScript?.let { File(it.path) } ?: run {
            val basePath = requireNotNull(project.basePath) { "This project has no local base directory." }
            uniqueFile(KttpProjectPath.getTempRequestsDir(basePath), GuiScriptGenerator.defaultFileName(draft))
        }
        val document = targetScript?.let {
            requireNotNull(
                FileDocumentManager.getInstance().getDocument(it)
            ) { "The selected script cannot be opened as a text document." }
        }
        WriteCommandAction.runWriteCommandAction(project) {
            if (document == null) {
                targetFile.parentFile?.mkdirs()
                targetFile.writeText(GuiScriptGenerator.render(draft), StandardCharsets.UTF_8)
            } else {
                val separator = when {
                    document.text.isBlank() || document.text.endsWith("\n\n") || document.text.endsWith("\r\n\r\n") -> ""
                    document.text.endsWith('\n') || document.text.endsWith('\r') -> "\n"
                    else -> "\n\n"
                }
                document.insertString(document.textLength, separator + GuiScriptGenerator.render(draft, document.text))
                PsiDocumentManager.getInstance(project).commitDocument(document)
                FileDocumentManager.getInstance().saveDocument(document)
            }
        }
        return requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFile)) {
            "The request script was written but could not be opened."
        }
    }

    /**
     * Writes generated DSL through a document command. Reuses a generated path only when disk and cached document
     * match its saved fingerprint; otherwise chooses a new path to preserve manual edits.
     */
    fun generate(draft: GuiDraft): VirtualFile {
        val target = resolveTarget(draft)
        target.parentFile?.mkdirs()
        val parent = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target.parentFile)) {
            "Cannot access the script directory"
        }
        parent.refresh(false, false)
        lateinit var virtualFile: VirtualFile
        WriteCommandAction.runWriteCommandAction(project) {
            virtualFile = parent.findOrCreateChildData(this, target.name)
            val document = requireNotNull(
                FileDocumentManager.getInstance().getDocument(virtualFile)
            ) { "Cannot open the script as a text document" }
            document.setText(GuiScriptGenerator.render(draft))
            PsiDocumentManager.getInstance(project).commitDocument(document)
            FileDocumentManager.getInstance().saveDocument(document)
        }
        return virtualFile
    }

    fun open(path: String?) {
        if (path != null) KttpFileNavigation.open(project, path)
    }

    private fun resolveTarget(draft: GuiDraft): File {
        draft.bridge.generatedScriptPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val diskFingerprint = sha256(file.readText())
                val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
                val document = virtualFile?.let { FileDocumentManager.getInstance().getCachedDocument(it) }
                val documentFingerprint = document?.text?.let(::sha256) ?: diskFingerprint
                if (diskFingerprint == draft.bridge.generatedScriptFingerprint && documentFingerprint == draft.bridge.generatedScriptFingerprint) return file
                Messages.showWarningDialog(
                    project,
                    "Generated script has changed outside GUI. A new script file will be created instead of overwriting it.",
                    "Generated Script Changed"
                )
            }
        }
        val directory =
            project.basePath?.let { KttpProjectPath.getRequestsDir(it) } ?: KttpConsolesPath.getKttpConsolesDir()
        return uniqueFile(directory, GuiScriptGenerator.defaultFileName(draft))
    }

    private fun uniqueFile(directory: File, name: String): File {
        var file = File(directory, name)
        var suffix = 1
        while (file.exists()) file = File(directory, "${name.removeSuffix(".kttp.kts")}-${suffix++}.kttp.kts")
        return file
    }

    private fun sha256(input: String): String = MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
}
