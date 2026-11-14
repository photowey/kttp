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
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import io.github.photowey.kttp.idea.execution.RequestExecutionMode
import io.github.photowey.kttp.idea.gui.DraftOwnership
import io.github.photowey.kttp.idea.gui.GuiDraft
import io.github.photowey.kttp.idea.gui.GuiDraftService
import io.github.photowey.kttp.idea.importing.ImportBundle
import io.github.photowey.kttp.idea.importing.ImportCommitResult
import io.github.photowey.kttp.idea.importing.ImportDestination
import io.github.photowey.kttp.idea.importing.ImportValue
import io.github.photowey.kttp.idea.importing.ImportedRequest
import io.github.photowey.kttp.idea.importing.RequestImportCoordinator
import java.awt.Component
import java.awt.Container

/**
 * Exercises persisted GUI imports through the editor's real load and Follow Script preparation boundary.
 * Preparation locates and saves a source without executing the script or sending HTTP.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
class ImportDraftSourceWorkflowTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()

    fun testImportedDraftRestoresWithoutImplicitEdtReadAccess() = withImported { result, saved ->
        val panel = RequestEditorPanel(project) { _, _ -> fail("Loading must not execute HTTP") }
        val application = ApplicationManager.getApplication()
        requireNotNull(application.threadingSupport)
            .releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
                assertTrue(application.isDispatchThread)
                assertFalse(application.isReadAccessAllowed)
                panel.loadDraft(saved)
                assertEquals(result.files.single(), panel.currentSourceFile)
                assertTrue(header(panel).sendButton.isEnabled)
                WriteCommandAction.runWriteCommandAction(project) {
                    val document = FileDocumentManager.getInstance().getDocument(result.files.single())!!
                    document.setText(document.text.replace("/custom", "/edited"))
                }
                assertFalse(application.isReadAccessAllowed)
                panel.loadDraft(saved)
                assertEquals("http://127.0.0.1/edited", header(panel).urlField.text)
                assertTrue(header(panel).sendButton.isEnabled)
            }
    }

    fun testImportedCustomMethodDraftCanPrepareAfterLoadAndArchiveReload() = withImported { result, saved ->
        var responses = 0
        val panel = RequestEditorPanel(project) { _, _ -> responses++ }
        panel.loadDraft(saved)
        assertEquals(result.files.single(), panel.currentSourceFile)
        assertEquals(0, panel.currentDeclarationIndex)
        assertTrue(header(panel).sendButton.isEnabled)
        assertEquals(RequestExecutionMode.FOLLOW_SCRIPT, header(panel).executionModeCombo.selectedItem)
        val prepared = prepare(panel)
        assertEquals(saved.id, prepared.draft.id)
        assertEquals(result.files.single(), prepared.file)
        assertEquals(0, prepared.declarationIndex)
        assertTrue(prepared.requestLines.isNotBlank())
        val reloaded = GuiDraftService(project).findDraft(saved.id)!!
        val reopened = RequestEditorPanel(project) { _, _ -> responses++ }
        reopened.loadDraft(reloaded)
        assertTrue(header(reopened).sendButton.isEnabled)
        assertEquals(saved.id, prepare(reopened).draft.id)
        assertEquals(0, responses)
    }

    fun testNamedRequestMovesAndChangesWithoutFollowingTheOldIndex() = withImported { result, saved ->
        val file = result.files.single()
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText(
                "GET(\"http://127.0.0.1/wrong\", name = \"Other\") {}\n" +
                    document.text.replace("/custom", "/updated")
            )
        }
        val panel = RequestEditorPanel(project) { _, _ -> fail("Preparing must not execute HTTP") }
        panel.loadDraft(saved)
        assertEquals(1, panel.currentDeclarationIndex)
        assertTrue(header(panel).sendButton.isEnabled)
        assertEquals("http://127.0.0.1/updated", header(panel).urlField.text)
        val prepared = prepare(panel)
        assertEquals(1, prepared.declarationIndex)
        assertEquals(saved.id, prepared.draft.id)
    }

    fun testMissingSourceCannotFallBackToDisplayedGetPlaceholder() = withImported { result, saved ->
        WriteCommandAction.runWriteCommandAction(project) { result.files.single().delete(this) }
        val panel = RequestEditorPanel(project) { _, _ -> fail("Missing imports must not execute") }
        panel.loadDraft(saved)
        assertNull(panel.currentSourceFile)
        assertNull(panel.currentDeclarationIndex)
        assertFalse(header(panel).sendButton.isEnabled)
        assertTrue(header(panel).sendButton.toolTipText.contains("unavailable"))
        assertEquals(DraftOwnership.SCRIPT_IMPORTED, saved.ownership)
    }

    fun testReplacedOrAmbiguousRequestDoesNotReusePersistedIndex() = withImported { result, saved ->
        val document = FileDocumentManager.getInstance().getDocument(result.files.single())!!
        val original = document.text
        val panel = RequestEditorPanel(project) { _, _ -> fail("Unidentified requests must not execute") }
        for (changed in listOf(original.replace("Imported custom", "Replacement"), original + original)) {
            WriteCommandAction.runWriteCommandAction(project) { document.setText(changed) }
            panel.loadDraft(saved)
            assertNull(panel.currentSourceFile)
            assertNull(panel.currentDeclarationIndex)
            assertFalse(header(panel).sendButton.isEnabled)
        }
    }

    fun testImportedEnvironmentUrlRemainsReadableAfterSourceRestoration() {
        val request = ImportedRequest("Read", "GET", ImportValue("{{baseUrl}}/echo?source=postman", true))
        val result = RequestImportCoordinator(project).commit(
            ImportBundle(requests = listOf(request)), setOf(0), ImportDestination.Drafts, project.basePath
        )
        try {
            val panel = RequestEditorPanel(project) { _, _ -> fail("Loading must not execute HTTP") }
            panel.loadDraft(result.drafts.single())
            assertEquals("\${baseUrl}/echo?source=postman", header(panel).urlField.text)
            assertFalse(header(panel).urlField.isEditable)
            assertEquals(RequestExecutionMode.FOLLOW_SCRIPT, header(panel).executionModeCombo.selectedItem)
            assertEquals(result.files.single(), prepare(panel).file)
        } finally {
            project.getService(GuiDraftService::class.java).deleteDraft(result.drafts.single().id)
            WriteCommandAction.runWriteCommandAction(project) { result.files.forEach { it.delete(this) } }
        }
    }

    private fun withImported(action: (ImportCommitResult, GuiDraft) -> Unit) {
        val bundle = ImportBundle(
            requests = listOf(
                ImportedRequest("Imported custom", "PROPFIND", ImportValue("http://127.0.0.1/custom"))
            )
        )
        val result = RequestImportCoordinator(project).commit(bundle, setOf(0), ImportDestination.Drafts, null)
        val saved = GuiDraftService(project).findDraft(result.drafts.single().id)!!
        try {
            assertEquals(DraftOwnership.SCRIPT_IMPORTED, saved.ownership)
            action(result, saved)
        } finally {
            result.drafts.forEach { project.getService(GuiDraftService::class.java).deleteDraft(it.id) }
            WriteCommandAction.runWriteCommandAction(project) {
                result.files.filter { it.isValid }.forEach { it.delete(this) }
            }
        }
    }

    private fun prepare(panel: RequestEditorPanel): PreparedScriptRequest {
        val method = RequestEditorPanel::class.java.getDeclaredMethod("prepareScript").apply { isAccessible = true }
        return method.invoke(panel) as PreparedScriptRequest
    }

    private fun header(panel: RequestEditorPanel): ComposerHeaderPanel =
        descendants(panel).filterIsInstance<ComposerHeaderPanel>().single()

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
    }
}
