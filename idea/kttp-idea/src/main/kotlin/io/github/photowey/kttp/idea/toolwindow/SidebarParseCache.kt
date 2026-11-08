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

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile

/**
 * Schedules background parsing and publication of sidebar results.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal interface SidebarTaskScheduler {
    fun background(task: () -> Unit)
    fun publish(task: () -> Unit)
}

/**
 * Runs sidebar parsing on the application pool and publishes results on the UI thread.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal object ApplicationSidebarTaskScheduler : SidebarTaskScheduler {
    override fun background(task: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread(task)
    }

    override fun publish(task: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(task)
    }
}

/**
 * Captures cache invalidation state for a scheduled sidebar parse.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal data class SidebarCacheVersion(val epoch: Long, val generations: Map<String, Long>)

/**
 * A read-action snapshot. Declarations contain smart pointers, never retained raw PSI files.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal data class SidebarParsedFile(
    val file: VirtualFile,
    val path: String,
    val modificationStamp: Long,
    val documentStamp: Long?,
    val epoch: Long,
    val generation: Long,
    val requests: List<SidebarRequestDisplayInfo>
)

/**
 * All mutable cache/version state belongs to the EDT; background work only produces snapshots.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal class SidebarParseCache(
    private val project: Project,
    private val scheduler: SidebarTaskScheduler = ApplicationSidebarTaskScheduler,
    parentDisposable: Disposable = project
) : Disposable {
    /**
     * Caches a parsed script together with its file-level projection summary.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    private data class Entry(val parsed: SidebarParsedFile, val summary: FileProjectionSummary)

    private val parser = SidebarRequestParser(project)
    private val entries = mutableMapOf<String, Entry>()
    private val generations = mutableMapOf<String, Long>()
    private var epoch = 0L
    @Volatile
    private var disposed = false

    init {
        Disposer.register(parentDisposable, this)
    }

    fun requests(path: String): List<SidebarRequestDisplayInfo>? = currentEntry(path)?.parsed?.requests
    fun summary(path: String): FileProjectionSummary? = currentEntry(path)?.summary

    fun captureVersion(): SidebarCacheVersion {
        assertOwner()
        return SidebarCacheVersion(epoch, generations.toMap())
    }

    /** Invalidates one path or the whole cache on EDT, advancing versions so queued stale parses cannot publish. */
    fun invalidate(path: String? = null) {
        assertOwner()
        if (path == null) {
            epoch++
            entries.clear()
            generations.clear()
        } else {
            generations[path] = (generations[path] ?: 0L) + 1L
            entries.remove(path)
        }
    }

    /** Safe on a background thread: the version is an immutable copy captured by the owner. */
    fun read(file: VirtualFile, version: SidebarCacheVersion): SidebarParsedFile? =
        ReadAction.compute<SidebarParsedFile?, RuntimeException> {
            if (disposed || project.isDisposed || !file.isValid) return@compute null
            val path = file.path
            val stamp = file.modificationStamp
            val requests = parser.read(file).toList()
            SidebarParsedFile(
                file, path, stamp, documentStamp(file), version.epoch,
                version.generations[path] ?: 0L, requests
            )
        }

    /** Publishes on EDT only when lifecycle, path, file/document stamps, and generation still match the parsed snapshot. */
    fun publish(parsed: SidebarParsedFile): Boolean {
        assertOwner()
        if (!isCurrent(parsed)) return false
        entries[parsed.path] = Entry(parsed, RequestProjectionPresentation.summary(parsed.requests.map { it.mode }))
        return true
    }

    fun parseSync(file: VirtualFile): List<SidebarRequestDisplayInfo> {
        assertOwner()
        invalidate(file.path)
        val parsed = read(file, captureVersion()) ?: return emptyList()
        return if (publish(parsed)) parsed.requests else emptyList()
    }

    /**
     * Uses a valid cached result or parses in the background, then revalidates before an EDT callback.
     * Invalidated, modified, or disposed results are dropped without invoking the callback.
     */
    fun request(file: VirtualFile, onPublished: (List<SidebarRequestDisplayInfo>) -> Unit) {
        assertOwner()
        if (disposed || project.isDisposed || !file.isValid) return
        val cached = currentEntry(file.path)?.parsed
        if (cached != null) {
            scheduler.publish { if (isCurrent(cached)) onPublished(cached.requests) }
            return
        }
        invalidate(file.path)
        val version = captureVersion()
        scheduler.background {
            val parsed = read(file, version)
            scheduler.publish { if (parsed != null && publish(parsed)) onPublished(parsed.requests) }
        }
    }

    override fun dispose() {
        disposed = true
        if (ApplicationManager.getApplication().isDispatchThread) invalidate()
        else scheduler.publish { invalidate() }
    }

    private fun currentEntry(path: String): Entry? {
        assertOwner()
        if (disposed || project.isDisposed) return null
        val entry = entries[path] ?: return null
        if (isCurrent(entry.parsed)) return entry
        invalidate(path)
        return null
    }

    private fun isCurrent(parsed: SidebarParsedFile): Boolean =
        !disposed && !project.isDisposed && parsed.epoch == epoch &&
            parsed.generation == (generations[parsed.path] ?: 0L) && parsed.file.isValid &&
            parsed.file.path == parsed.path && parsed.file.modificationStamp == parsed.modificationStamp &&
            documentUnchanged(parsed)

    private fun documentUnchanged(parsed: SidebarParsedFile): Boolean {
        val manager = FileDocumentManager.getInstance()
        val currentStamp = manager.getCachedDocument(parsed.file)?.modificationStamp
        return if (parsed.documentStamp == null) !manager.isFileModified(parsed.file)
        else currentStamp == parsed.documentStamp
    }

    private fun documentStamp(file: VirtualFile): Long? =
        FileDocumentManager.getInstance().getCachedDocument(file)?.modificationStamp

    private fun assertOwner() {
        ApplicationManager.getApplication().assertIsDispatchThread()
    }
}
