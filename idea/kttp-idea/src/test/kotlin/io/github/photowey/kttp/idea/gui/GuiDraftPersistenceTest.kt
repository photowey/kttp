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

import com.intellij.testFramework.LightPlatformTestCase
import io.github.photowey.kttp.idea.path.KttpConsolesPath
import io.github.photowey.kttp.idea.path.KttpProjectPath
import java.io.File

/**
 * Verifies draft persistence failure handling and preservation of existing archives and snapshots.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class GuiDraftPersistenceTest : LightPlatformTestCase() {
    fun testNewGuiDraftHasAnUnsavedHttpbinExampleAndUsableScriptPreview() = withArchive { file ->
        val service = GuiDraftService(project)
        val draft = service.createNewDraft()
        assertEquals(ValueRef.Literal("https://httpbin.org/get"), draft.request.target.url)
        assertEquals(listOf(NameValueRef("source", ValueRef.Literal("kttp"))), draft.request.target.queryParams)
        assertEquals(listOf(HeaderSpec("Accept", ValueRef.Literal("application/json"))), draft.request.headers)
        assertEquals(io.github.photowey.kttp.idea.execution.RequestExecutionMode.QUICK_REQUEST, draft.executionMode)
        assertNull(draft.bridge.sourceScriptPath)
        assertNull(draft.bridge.generatedScriptPath)
        assertFalse("Creating an example must not save it", file.exists())
        assertEquals(
            "GET(\"https://httpbin.org/get\") {\n" +
                "    query(\"source\", \"kttp\")\n" +
                "    header(\"Accept\", \"application/json\")\n}\n",
            GuiScriptGenerator.render(draft)
        )
        assertTrue("Programmatic empty drafts remain empty", service.createEmptyDraft().request.target.queryParams.isEmpty())
    }

    fun testCreatingAnExampleDoesNotChangeAnExistingSavedDraft() = withArchive { file ->
        val service = GuiDraftService(project)
        val saved = service.saveDraft(service.createEmptyDraft().copy(title = "Existing"))
        val previousArchive = file.readText()
        val example = service.createNewDraft()
        assertTrue(saved.id != example.id)
        assertEquals(previousArchive, file.readText())
        assertEquals(saved, service.findDraft(saved.id))
    }

    fun testCorruptReadFollowedBySavePreservesTheOriginalArchiveAndReportsFailure() = withArchive { file ->
        val corrupted = "{ damaged draft archive"
        file.writeText(corrupted)
        val service = GuiDraftService(project)
        val read = runCatching { service.listDrafts() }
        val save = runCatching { service.saveDraft(service.createEmptyDraft()) }
        assertEquals(corrupted, file.readText())
        assertTrue("Corrupt drafts must not look like an empty archive", read.isFailure)
        assertTrue("Save must report unreadable drafts", save.isFailure)
    }

    fun testCorruptArchiveAfterCacheLoadCannotBeOverwritten() = withArchive { file ->
        val service = GuiDraftService(project)
        val saved = service.saveDraft(service.createEmptyDraft().copy(title = "original"))
        val corrupted = "{ externally damaged archive"
        file.writeText(corrupted)
        assertTrue(runCatching { service.renameDraft(saved.id, "new title") }.isFailure)
        assertEquals(corrupted, file.readText())
        assertEquals("original", service.findDraft(saved.id)!!.title)
    }

    fun testUnreadableArchiveCannotBeTreatedAsMissingAndReplaced() = withArchive { file ->
        file.mkdir()
        val service = GuiDraftService(project)
        assertTrue(
            "An archive path that is a directory is a read failure",
            runCatching { service.listDrafts() }.isFailure
        )
        assertTrue(runCatching { service.saveDraft(service.createEmptyDraft()) }.isFailure)
        assertTrue(file.isDirectory)
    }

    fun testFailedSaveKeepsThePreviouslyPublishedDraftSnapshot() = withArchive { file ->
        val service = GuiDraftService(project)
        val saved = service.saveDraft(service.createEmptyDraft().copy(title = "original"))
        val bytes = file.readBytes()
        file.delete()
        file.mkdir()
        try {
            assertTrue(runCatching { service.saveDraft(saved.copy(title = "failed change")) }.isFailure)
            assertEquals("original", service.findDraft(saved.id)!!.title)
        } finally {
            file.delete()
            file.writeBytes(bytes)
        }
        assertEquals("original", GuiDraftService(project).findDraft(saved.id)!!.title)
    }

    fun testMutatingInputAndReturnedNestedListsCannotChangeStoredDrafts() = withArchive {
        val service = GuiDraftService(project)
        val headers = mutableListOf(HeaderSpec("Original", ValueRef.Literal("value")))
        val draft = service.saveDraft(service.createEmptyDraft().copy(request = RequestSpec(headers = headers)))
        headers.clear()
        assertEquals(listOf("Original"), service.findDraft(draft.id)!!.request.headers.map { it.name })
        val exposed = service.findDraft(draft.id)!!.request.headers
        runCatching { (exposed as MutableList<HeaderSpec>).clear() }
        assertEquals(listOf("Original"), service.findDraft(draft.id)!!.request.headers.map { it.name })
    }

    private fun withArchive(action: (File) -> Unit) {
        val directory = project.basePath?.let(KttpProjectPath::getGuiStateDir) ?: KttpConsolesPath.getGuiStateDir()
        val file = File(directory, "drafts.json")
        val previous = file.takeIf(File::isFile)?.readBytes()
        file.delete()
        try {
            action(file)
        } finally {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (previous != null) file.writeBytes(previous)
        }
    }
}
