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
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import io.github.photowey.kttp.idea.gui.DraftOwnership
import io.github.photowey.kttp.idea.gui.GuiDraft
import io.github.photowey.kttp.idea.script.KttpRequestDeclaration
import io.github.photowey.kttp.idea.script.toKttpScript
import org.jetbrains.kotlin.psi.KtFile

/**
 * Rehydrates persisted imported drafts from source identity, never from a stale index alone.
 * A uniquely named declaration may move or change; unnamed declarations require the loader's stable identity.
 * Missing, replaced or ambiguous sources remain unavailable until the user selects a request explicitly.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal class PersistedDraftSourceResolver(private val project: Project) {
    fun restore(session: EditorWorkingSession): KttpRequestDeclaration? {
        val source = resolve(session.draft) ?: return null
        session.accept(source.draft)
        session.sourceFile = source.file
        session.declarationIndex = source.index
        return source.declaration
    }

    private fun resolve(draft: GuiDraft): PersistedDraftSource? = try {
        resolveCurrent(draft)
    } catch (cancelled: ProcessCanceledException) {
        throw cancelled
    } catch (_: RuntimeException) {
        // Invalid PSI or a disappearing source must leave Follow Script unavailable.
        null
    }

    private fun resolveCurrent(draft: GuiDraft): PersistedDraftSource? {
        if (draft.ownership != DraftOwnership.SCRIPT_IMPORTED || project.isDisposed) return null
        val identity = draft.request.identity
        val path = draft.bridge.sourceScriptPath ?: identity.sourceFile ?: return null
        if (identity.sourceFile != null && identity.sourceFile != path) return null
        val file = LocalFileSystem.getInstance().findFileByPath(path)?.takeIf { it.isValid && !it.isDirectory }
            ?: return null
        val document = ApplicationManager.getApplication().runReadAction(Computable {
            FileDocumentManager.getInstance().getDocument(file)
        }) ?: return null
        ApplicationManager.getApplication().runWriteAction {
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
        return ApplicationManager.getApplication().runReadAction(Computable {
            val psi = PsiManager.getInstance(project).findFile(file) as? KtFile ?: return@Computable null
            val declarations = psi.toKttpScript()?.findRequestDeclarations().orEmpty()
            val selected = select(draft, file, declarations) ?: return@Computable null
            val projected = RequestEditorProjectionLoader(project).load(declarations[selected], file, selected)
            PersistedDraftSource(
                projected.copy(
                    id = draft.id,
                    title = draft.title,
                    meta = draft.meta,
                    executionMode = draft.executionMode
                ),
                file, selected, declarations[selected]
            )
        })
    }

    private fun select(draft: GuiDraft, file: VirtualFile, declarations: List<KttpRequestDeclaration>): Int? {
        val name = draft.request.identity.name?.takeIf { it.isNotBlank() }
        val named =
            if (name == null) emptyList() else declarations.indices.filter { declarations[it].getName() == name }
        if (named.size == 1) return named.single()
        if (!draft.id.startsWith("import:${file.path}:")) return null
        val candidates = if (name == null) declarations.indices.toList() else named
        val loader = RequestEditorProjectionLoader(project)
        return candidates.filter { loader.load(declarations[it], file, it).id == draft.id }.singleOrNull()
    }
}

/**
 * A current, safely identified source selection and its refreshed projection, retaining the persisted draft ID.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal data class PersistedDraftSource(
    val draft: GuiDraft,
    val file: VirtualFile,
    val index: Int,
    val declaration: KttpRequestDeclaration
)

/**
 * Refreshes source semantics without replacing the persisted GUI draft's identity or result metadata.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal fun PreparedScriptRequest.withPersistedIdentity(persisted: GuiDraft): PreparedScriptRequest =
    if (draft.ownership != DraftOwnership.SCRIPT_IMPORTED) this else copy(
        draft = draft.copy(id = persisted.id, title = persisted.title, meta = persisted.meta)
    )
