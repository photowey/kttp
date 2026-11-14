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

package io.github.photowey.kttp.idea.importing

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtil
import io.github.photowey.kttp.idea.path.KttpDocumentSaver
import java.util.UUID

/**
 * Writes an already prepared import under one IDE command and restores only this operation on failure.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal class ImportTargetWriter(private val project: Project) {
    private val originals = linkedMapOf<Document, String>()
    private val created = mutableListOf<VirtualFile>()

    fun <T> write(action: ImportTargetWriter.() -> T): T {
        var result: Result<T>? = null
        WriteCommandAction.runWriteCommandAction(project, "Import Kttp Requests", null, Runnable {
            val outcome = runCatching { action() }
            result = outcome
            outcome.exceptionOrNull()?.let(::rollback)
        })
        return requireNotNull(result).getOrThrow()
    }

    fun replace(document: Document, text: String) {
        require(document.isWritable) { "Import target is read-only" }
        originals.putIfAbsent(document, document.text)
        document.setText(text)
    }

    fun create(directory: VirtualFile, suffix: String, text: String): VirtualFile {
        val name = "import-${UUID.randomUUID()}$suffix"
        val file = directory.createChildData(this, name)
        created += file
        replace(requireNotNull(FileDocumentManager.getInstance().getDocument(file)), text)
        return file
    }

    fun createScript(directory: VirtualFile, requestedName: String, text: String): VirtualFile {
        require(
            requestedName.endsWith(".kttp.kts") && requestedName.removeSuffix(".kttp.kts").isNotBlank() &&
                requestedName.none { it == '/' || it == '\\' || it.isISOControl() }) { "Choose a .kttp.kts file name without directory separators" }
        val base = requestedName.removeSuffix(".kttp.kts")
        var name = requestedName
        var index = 2
        while (directory.findChild(name) != null) name = "$base-${index++}.kttp.kts"
        val file = directory.createChildData(this, name)
        created += file
        replace(requireNotNull(FileDocumentManager.getInstance().getDocument(file)), text)
        return file
    }

    fun environment(directory: VirtualFile, text: String) {
        val existing = directory.findChild("kttp.env.json")
        val file = existing ?: directory.createChildData(this, "kttp.env.json").also { created += it }
        require(file.isWritable) { "Environment file is read-only" }
        replace(requireNotNull(FileDocumentManager.getInstance().getDocument(file)), text)
    }

    fun save() {
        originals.keys.forEach(KttpDocumentSaver::save)
    }

    private fun rollback(failure: Throwable) {
        originals.forEach { (document, text) -> runCatching { document.setText(text) }.onFailure(failure::addSuppressed) }
        created.asReversed()
            .forEach { file -> runCatching { if (file.isValid) file.delete(this) }.onFailure(failure::addSuppressed) }
        originals.keys.forEach {
            runCatching {
                if (FileDocumentManager.getInstance().getFile(it)?.isValid == true) KttpDocumentSaver.save(it)
            }.onFailure(failure::addSuppressed)
        }
    }
}
