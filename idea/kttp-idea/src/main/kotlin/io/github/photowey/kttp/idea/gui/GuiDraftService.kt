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

package io.github.photowey.kttp.idea.gui

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.github.photowey.kttp.idea.io.AtomicStateFile
import io.github.photowey.kttp.idea.io.StatePersistenceException
import io.github.photowey.kttp.idea.path.KttpConsolesPath
import io.github.photowey.kttp.idea.path.KttpProjectPath
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.Collections

/**
 * Maintains immutable project draft snapshots and an atomic JSON archive.
 * Synchronized persistence publishes cache changes only after a successful write.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
@Service(Service.Level.PROJECT)
class GuiDraftService(private val project: Project) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val stateFile: File by lazy {
        project.basePath?.let { File(KttpProjectPath.getGuiStateDir(it), "drafts.json") }
            ?: File(KttpConsolesPath.getGuiStateDir(), "drafts.json")
    }

    private var cache: List<GuiDraft>? = null

    @Synchronized
    fun listDrafts(): List<GuiDraft> = immutableList(loadCache().sortedBy { it.title.lowercase() })

    @Synchronized
    fun findDraft(id: String): GuiDraft? = loadCache().firstOrNull { it.id == id }

    /** Creates a detached in-memory draft; no archive is written until a persistence operation is requested. */
    fun createEmptyDraft(): GuiDraft {
        return GuiDraft(
            id = UUID.randomUUID().toString(),
            title = "New Request",
            ownership = DraftOwnership.DETACHED,
            request = RequestSpec()
        )
    }

    /**
     * Persists an immutable GUI-owned snapshot and clears its dirty flag.
     * The cache changes only after the archive is successfully written; persistence failures propagate.
     */
    @Synchronized
    fun saveDraft(draft: GuiDraft): GuiDraft {
        val persisted = immutableDraft(
            draft.copy(
                ownership = DraftOwnership.GUI_OWNED,
                meta = draft.meta.copy(dirty = false)
            )
        )
        val drafts = loadCache().toMutableList()
        val idx = drafts.indexOfFirst { it.id == persisted.id }
        if (idx >= 0) {
            drafts[idx] = persisted
        } else {
            drafts.add(0, persisted)
        }
        flush(drafts)
        return persisted
    }

    @Synchronized
    fun renameDraft(id: String, newTitle: String): GuiDraft? {
        val drafts = loadCache().toMutableList()
        val idx = drafts.indexOfFirst { it.id == id }
        if (idx < 0) return null
        val updated = drafts[idx].copy(title = newTitle.trim().ifBlank { drafts[idx].title })
        drafts[idx] = updated
        flush(drafts)
        return updated
    }

    /** Persists a copy with a new ID and cleared bridge file links, retaining the request and execution choice. */
    @Synchronized
    fun duplicateDraft(id: String): GuiDraft? {
        val source = findDraft(id) ?: return null
        val duplicated = source.copy(
            id = UUID.randomUUID().toString(),
            title = buildDuplicateTitle(source.title),
            ownership = DraftOwnership.DETACHED,
            bridge = source.bridge.copy(
                sourceScriptPath = null,
                generatedScriptPath = null,
                generatedScriptFingerprint = null
            ),
            meta = DraftMeta(dirty = true)
        )
        val drafts = loadCache().toMutableList()
        drafts.add(0, duplicated)
        flush(drafts)
        return duplicated
    }

    @Synchronized
    fun deleteDraft(id: String) {
        val drafts = loadCache().toMutableList()
        drafts.removeAll { it.id == id }
        flush(drafts)
    }

    /** Records the generated file fingerprint and timestamp, then persists the draft as GUI-owned. */
    @Synchronized
    fun markGenerated(draft: GuiDraft, scriptPath: String): GuiDraft {
        val fingerprint = sha256(File(scriptPath).takeIf(File::exists)?.readText() ?: "")
        val updated = draft.copy(
            bridge = draft.bridge.copy(
                generatedScriptPath = scriptPath,
                generatedScriptFingerprint = fingerprint
            ),
            meta = draft.meta.copy(
                dirty = false,
                lastGeneratedAt = System.currentTimeMillis()
            )
        )
        return saveDraft(updated)
    }

    /** Updates send metadata; only GUI-owned drafts are persisted, while other drafts return an immutable snapshot. */
    @Synchronized
    fun markSent(draft: GuiDraft, responseFilePath: String?): GuiDraft {
        val updated = draft.copy(
            meta = draft.meta.copy(
                lastSentAt = System.currentTimeMillis(),
                lastResultRef = responseFilePath
            )
        )
        return if (updated.ownership == DraftOwnership.GUI_OWNED) saveDraft(updated) else immutableDraft(updated)
    }

    private fun loadCache(): List<GuiDraft> {
        cache?.let { return it }
        val text = AtomicStateFile.read(stateFile)
        val loaded = if (text == null) emptyList() else decodeDrafts(text)
        return immutableList(loaded.map(::immutableDraft)).also { cache = it }
    }

    private fun decodeDrafts(text: String): List<GuiDraft> = try {
        json.decodeFromString(StoredDrafts.serializer(), text).drafts
    } catch (failure: SerializationException) {
        throw StatePersistenceException("Invalid draft archive: ${stateFile.absolutePath}", failure)
    } catch (failure: IllegalArgumentException) {
        throw StatePersistenceException("Invalid draft archive: ${stateFile.absolutePath}", failure)
    }

    private fun flush(drafts: List<GuiDraft>) {
        // A file changed outside this service must still be recoverable if it becomes unreadable.
        AtomicStateFile.read(stateFile)?.let(::decodeDrafts)
        val published = immutableList(drafts.map(::immutableDraft))
        val text = json.encodeToString(StoredDrafts.serializer(), StoredDrafts(published))
        AtomicStateFile.replace(stateFile, text)
        cache = published
    }

    private fun immutableDraft(draft: GuiDraft): GuiDraft = draft.copy(
        bridge = draft.bridge.copy(gaps = immutableList(draft.bridge.gaps)),
        request = draft.request.copy(
            target = draft.request.target.copy(
                pathParams = immutableList(draft.request.target.pathParams),
                queryParams = immutableList(draft.request.target.queryParams)
            ),
            headers = immutableList(draft.request.headers),
            body = when (val body = draft.request.body) {
                is BodySpec.FormUrlEncoded -> body.copy(fields = immutableList(body.fields))
                else -> body
            }
        )
    )

    private fun <T> immutableList(values: List<T>): List<T> = Collections.unmodifiableList(values.toList())

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun buildDuplicateTitle(original: String): String {
        val base = original.ifBlank { "Draft" }
        return if (base.startsWith("Copy of ")) "$base 2" else "Copy of $base"
    }

    /**
     * Represents the serialized collection of GUI drafts.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    @Serializable
    private data class StoredDrafts(val drafts: List<GuiDraft> = emptyList())
}
