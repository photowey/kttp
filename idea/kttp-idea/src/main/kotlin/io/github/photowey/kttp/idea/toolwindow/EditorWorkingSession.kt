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

import com.intellij.openapi.vfs.VirtualFile
import io.github.photowey.kttp.idea.gui.*
import io.github.photowey.kttp.idea.execution.RequestExecutionMode
import java.util.UUID

/**
 * The editor's working copy. Persisted snapshots never replace a dirty request with the same ID.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal class EditorWorkingSession(initial: GuiDraft) {
    var draft: GuiDraft = initial
        private set
    var revision: Long = 0
        private set
    var sourceFile: VirtualFile? = null
    var declarationIndex: Int? = null

    val editable: Boolean get() = draft.ownership != DraftOwnership.SCRIPT_IMPORTED && draft.bridge.mode == GuiMode.GUI_READY
    val linkedScriptPath: String? get() = draft.bridge.generatedScriptPath ?: draft.bridge.sourceScriptPath

    /** Loads persisted metadata while retaining a dirty request with the same ID; advances the revision and clears source selection. */
    fun loadPersisted(incoming: GuiDraft) {
        val previous = draft
        draft = if (incoming.id == previous.id && previous.meta.dirty) {
            incoming.copy(
                request = previous.request,
                ownership = previous.ownership,
                bridge = previous.bridge,
                executionMode = previous.executionMode,
                meta = incoming.meta.copy(dirty = true)
            )
        } else incoming
        sourceFile = null
        declarationIndex = null
        revision++
    }

    fun loadImported(incoming: GuiDraft, file: VirtualFile?, index: Int?) {
        draft = incoming
        sourceFile = file
        declarationIndex = index
        revision++
    }

    fun edit(updated: GuiDraft) {
        draft = updated.copy(meta = updated.meta.copy(dirty = true))
        revision++
    }

    fun selectExecutionMode(mode: RequestExecutionMode) {
        if (mode == draft.executionMode) return
        draft = draft.copy(executionMode = mode)
        revision++
    }

    /** Accepts operation metadata without advancing the edit revision, so an in-flight send can still match its working copy. */
    fun accept(updated: GuiDraft) {
        draft = updated
    }

    /** Creates an editable Quick Request copy of an imported, sufficiently projected request and clears active source selection. */
    fun detach() {
        if (draft.ownership != DraftOwnership.SCRIPT_IMPORTED || draft.bridge.mode == GuiMode.SCRIPT_ONLY) return
        draft = draft.copy(
            id = UUID.randomUUID().toString(), ownership = DraftOwnership.DETACHED,
            bridge = ScriptBridgeState(sourceScriptPath = draft.bridge.sourceScriptPath, mode = GuiMode.GUI_READY),
            meta = DraftMeta(dirty = true), executionMode = RequestExecutionMode.QUICK_REQUEST
        )
        sourceFile = null
        declarationIndex = null
        revision++
    }

    /** Checks both identity and edit revision before send completion updates the current draft metadata. */
    fun stillMatches(sent: GuiDraft, sentRevision: Long): Boolean = draft.id == sent.id && revision == sentRevision
}
