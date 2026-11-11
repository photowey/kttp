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

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import io.github.photowey.kttp.idea.gui.DraftOwnership
import io.github.photowey.kttp.idea.gui.GuiDraft
import io.github.photowey.kttp.idea.gui.GuiDraftService
import io.github.photowey.kttp.idea.script.KttpRequestDeclaration
import io.github.photowey.kttp.idea.script.sourceLines
import io.github.photowey.kttp.idea.script.toKttpScript
import org.jetbrains.kotlin.psi.KtFile

/**
 * Pairs a prepared draft with its saved script file and selected request coordinates.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal data class PreparedScriptRequest(
    val draft: GuiDraft,
    val file: VirtualFile,
    val declarationIndex: Int,
    val requestLines: String
)

/**
 * Prepares Follow Script source documents and current request coordinates on EDT.
 * Independent drafts generate a script through fingerprint conflict protection before execution.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal class ScriptExecutionPreparation(private val project: Project) {
    /**
     * Prepares a single Follow Script target on EDT: saves the selected source or generates and persists a draft script.
     * Returns current source-line coordinates and does not execute the script.
     */
    fun prepare(draft: GuiDraft, source: VirtualFile?, selected: KttpRequestDeclaration?): PreparedScriptRequest {
        return if (draft.ownership == DraftOwnership.SCRIPT_IMPORTED) prepareSource(draft, source, selected)
        else {
            val file = RequestScriptFileCoordinator(project).generate(draft)
            val saved = project.getService(GuiDraftService::class.java).markGenerated(draft, file.path)
            val declaration = (PsiManager.getInstance(project).findFile(file) as? KtFile)
                ?.toKttpScript()?.findRequestDeclarations()?.singleOrNull()
            PreparedScriptRequest(
                saved,
                file,
                0,
                requireNotNull(declaration) { "Cannot locate the generated request." }.sourceLines().asString()
            )
        }
    }

    /**
     * Commits and saves the selected source document, then reidentifies the request by its PSI element.
     * A deleted or invalid selection fails instead of falling back to its old index or the whole file.
     */
    private fun prepareSource(
        draft: GuiDraft,
        source: VirtualFile?,
        selected: KttpRequestDeclaration?
    ): PreparedScriptRequest {
        val file =
            requireNotNull(source?.takeIf { it.isValid }) { "The source script is unavailable. Select the request again." }
        val documents = FileDocumentManager.getInstance()
        val document = requireNotNull(documents.getDocument(file)) { "Cannot read the source script." }
        PsiDocumentManager.getInstance(project).commitDocument(document)
        val element = requireNotNull(selected?.element) { "The selected request was removed. Select a request again." }
        val psiFile = PsiManager.getInstance(project).findFile(file) as? KtFile
        val declarations = psiFile?.toKttpScript()?.findRequestDeclarations().orEmpty()
        val index = declarations.indexOfFirst { it.element == element }
        require(index >= 0) { "The selected request is no longer available. Select a request again." }
        documents.saveDocument(document)
        check(!documents.isDocumentUnsaved(document)) { "Save the source script before sending this request." }
        val refreshed = RequestEditorProjectionLoader(project).load(declarations[index], file, index)
            .copy(executionMode = draft.executionMode)
        return PreparedScriptRequest(refreshed, file, index, declarations[index].sourceLines().asString())
    }
}
