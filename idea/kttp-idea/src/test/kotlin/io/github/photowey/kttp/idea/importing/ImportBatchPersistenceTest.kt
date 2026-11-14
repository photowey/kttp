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

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.photowey.kttp.idea.gui.*
import io.github.photowey.kttp.idea.path.KttpConsolesPath
import io.github.photowey.kttp.idea.path.KttpProjectPath
import java.io.File

/**
 * Verifies a failed batch never publishes a partial draft list and preserves script ownership.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
class ImportBatchPersistenceTest : BasePlatformTestCase() {
    fun testFailedBatchKeepsExistingSnapshots() = withArchive { file ->
        val service = GuiDraftService(project)
        val old = service.saveDraft(service.createEmptyDraft().copy(title = "Existing"))
        val bytes = file.readBytes()
        file.delete()
        file.mkdir()
        try {
            val batch = listOf(service.createEmptyDraft(), service.createEmptyDraft())
            assertTrue(runCatching { service.saveImportedDrafts(batch) }.isFailure)
            assertEquals(listOf(old), service.listDrafts())
            batch.forEach { assertNull(service.findDraft(it.id)) }
        } finally {
            file.delete(); file.writeBytes(bytes)
        }
    }

    fun testSuccessfulBatchRetainsScriptOnlyOwnership() = withArchive {
        val service = GuiDraftService(project)
        val linked = service.createEmptyDraft().copy(
            ownership = DraftOwnership.SCRIPT_IMPORTED,
            bridge = ScriptBridgeState(sourceScriptPath = "/test/imported.kttp.kts", mode = GuiMode.SCRIPT_ONLY)
        )
        val saved = service.saveImportedDrafts(listOf(linked)).single()
        assertEquals(DraftOwnership.SCRIPT_IMPORTED, saved.ownership)
        assertEquals(GuiMode.SCRIPT_ONLY, saved.bridge.mode)
        assertEquals(saved, GuiDraftService(project).findDraft(saved.id))
    }

    private fun withArchive(action: (File) -> Unit) {
        val directory =
            project.basePath?.let { KttpProjectPath.getGuiStateDir(it) } ?: KttpConsolesPath.getGuiStateDir()
        val file = File(directory, "drafts.json")
        val previous = file.takeIf { it.isFile }?.readBytes()
        file.delete()
        try {
            action(file)
        } finally {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (previous != null) file.writeBytes(previous)
        }
    }
}
