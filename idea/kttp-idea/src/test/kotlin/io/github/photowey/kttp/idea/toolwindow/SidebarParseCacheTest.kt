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

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies cache invalidation rejects stale parsing results and queued publications.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class SidebarParseCacheTest : BasePlatformTestCase() {
    fun testOlderQueuedParseCannotPublishAfterTheNewFileVersion() {
        val file = script("older")
        val scheduler = QueuedScheduler()
        val cache = SidebarParseCache(project, scheduler)
        val published = mutableListOf<String>()
        cache.request(file) { published += it.single().url }
        scheduler.runBackground()
        edit(file, "newer")
        cache.invalidate(file.path)
        cache.request(file) { published += it.single().url }
        scheduler.runBackground()

        scheduler.runPublication(1)
        scheduler.runPublication()

        assertEquals(listOf("https://example.com/newer"), published)
        assertEquals("https://example.com/newer", cache.requests(file.path)!!.single().url)
    }

    fun testInvalidationBeforeBackgroundReadRejectsTheOutstandingTask() {
        val file = script("invalidated-before-read")
        val scheduler = QueuedScheduler()
        val cache = SidebarParseCache(project, scheduler)
        var published = false
        cache.request(file) { published = true }
        cache.invalidate(file.path)

        scheduler.runBackground()
        scheduler.runPublication()

        assertFalse(published)
        assertNull(cache.requests(file.path))
        assertNull(cache.summary(file.path))
    }

    fun testInvalidationAfterParsingRejectsTheQueuedPublication() {
        val file = script("invalidated-after-read")
        val scheduler = QueuedScheduler()
        val cache = SidebarParseCache(project, scheduler)
        var published = false
        cache.request(file) { published = true }
        scheduler.runBackground()
        cache.invalidate(file.path)

        scheduler.runPublication()

        assertFalse(published)
        assertNull(cache.requests(file.path))
        assertNull(cache.summary(file.path))
    }

    fun testModificationStampChangeWithoutAnExplicitEventRejectsTheResult() {
        val file = script("before-edit")
        val scheduler = QueuedScheduler()
        val cache = SidebarParseCache(project, scheduler)
        var published = false
        val originalStamp = file.modificationStamp
        cache.request(file) { published = true }
        scheduler.runBackground()
        edit(file, "after-edit")
        assertTrue(file.modificationStamp != originalStamp)

        scheduler.runPublication()

        assertFalse(published)
        assertNull(cache.requests(file.path))
        assertNull(cache.summary(file.path))
    }

    fun testUnsavedDocumentEditRejectsTheQueuedPsiResult() {
        val file = script("unsaved-before")
        val document = requireNotNull(FileDocumentManager.getInstance().getDocument(file))
        val scheduler = QueuedScheduler()
        val cache = SidebarParseCache(project, scheduler)
        var published = false
        cache.request(file) { published = true }
        scheduler.runBackground()
        val diskStamp = file.modificationStamp
        runWriteAction {
            document.setText("GET(\"https://example.com/unsaved-after\")")
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
        assertEquals(diskStamp, file.modificationStamp)

        scheduler.runPublication()

        assertFalse(published)
        assertNull(cache.requests(file.path))
    }

    fun testDeletedFileCannotPublishAQueuedResult() {
        val file = script("deleted")
        val scheduler = QueuedScheduler()
        val cache = SidebarParseCache(project, scheduler)
        var published = false
        cache.request(file) { published = true }
        scheduler.runBackground()
        runWriteAction { file.delete(this) }

        scheduler.runPublication()

        assertFalse(published)
        assertNull(cache.requests(file.path))
    }

    fun testBackgroundParsingDoesNotMutateTheOwnerCacheBeforePublication() {
        val file = script("owner-publication")
        val scheduler = QueuedScheduler()
        val cache = SidebarParseCache(project, scheduler)
        cache.request(file) {}

        scheduler.runBackground()

        assertNull(cache.requests(file.path))
        assertNull(cache.summary(file.path))
        scheduler.runPublication()
        assertEquals("https://example.com/owner-publication", cache.requests(file.path)!!.single().url)
    }

    fun testClearingTheWholeCacheRejectsEveryOutstandingGeneration() {
        val first = script("clear-first")
        val second = script("clear-second")
        val scheduler = QueuedScheduler()
        val cache = SidebarParseCache(project, scheduler)
        var publications = 0
        cache.request(first) { publications++ }
        cache.request(second) { publications++ }
        scheduler.runBackground()
        scheduler.runBackground()
        cache.invalidate()

        scheduler.runPublication()
        scheduler.runPublication()

        assertEquals(0, publications)
        assertNull(cache.requests(first.path))
        assertNull(cache.requests(second.path))
    }

    fun testDeferredCachedRequestIsRevalidatedBeforeItsCallback() {
        val file = script("cached-publication")
        val scheduler = QueuedScheduler()
        val cache = SidebarParseCache(project, scheduler)
        cache.request(file) {}
        scheduler.runBackground()
        scheduler.runPublication()
        var published = false
        cache.request(file) { published = true }
        cache.invalidate(file.path)

        scheduler.runPublication()

        assertFalse(published)
    }

    fun testDisposedLifecycleOwnerCannotPublishAQueuedResult() {
        val file = script("closing")
        val owner = Disposer.newCheckedDisposable("sidebar-cache-owner")
        try {
            val scheduler = QueuedScheduler()
            val cache = SidebarParseCache(project, scheduler, owner)
            var published = false
            cache.request(file) { published = true }
            scheduler.runBackground()
            Disposer.dispose(owner)
            assertFalse("The fixture project remains usable", project.isDisposed)

            scheduler.runPublication()

            assertFalse(published)
            assertNull(cache.requests(file.path))
            assertNull(cache.summary(file.path))
        } finally {
            if (!owner.isDisposed) Disposer.dispose(owner)
        }
    }

    private fun script(suffix: String): VirtualFile =
        myFixture.addFileToProject("$suffix.kttp.kts", "GET(\"https://example.com/$suffix\")").virtualFile

    private fun edit(file: VirtualFile, suffix: String) {
        runWriteAction {
            val document = requireNotNull(FileDocumentManager.getInstance().getDocument(file))
            document.setText("GET(\"https://example.com/$suffix\")")
            PsiDocumentManager.getInstance(project).commitDocument(document)
            FileDocumentManager.getInstance().saveDocument(document)
        }
    }

    /**
     * Queues background and publication callbacks for deterministic sidebar cache tests.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    private class QueuedScheduler : SidebarTaskScheduler {
        private val backgrounds = mutableListOf<() -> Unit>()
        private val publications = mutableListOf<() -> Unit>()
        override fun background(task: () -> Unit) {
            backgrounds += task
        }

        override fun publish(task: () -> Unit) {
            publications += task
        }

        fun runBackground() {
            backgrounds.removeAt(0).invoke()
        }

        fun runPublication(index: Int = 0) {
            publications.removeAt(index).invoke()
        }
    }
}
