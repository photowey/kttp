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
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import io.github.photowey.kttp.idea.KttpUiActions
import io.github.photowey.kttp.idea.gui.GuiDraftService
import io.github.photowey.kttp.idea.gui.RequestSpec
import io.github.photowey.kttp.idea.gui.RequestTarget
import io.github.photowey.kttp.idea.gui.ValueRef
import io.github.photowey.kttp.idea.script.toKttpScript
import java.io.File
import org.jetbrains.kotlin.psi.KtFile

/**
 * Verifies saved script preparation and current request coordinates before GUI Follow Script execution.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class ScriptExecutionPreparationTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()

    fun testGeneratedRequestPreparesFromAccessibilityCallbackWithoutImplicitWriteIntent() {
        val service = project.getService(GuiDraftService::class.java)
        val draft = service.createEmptyDraft().copy(
            request = RequestSpec(target = RequestTarget(ValueRef.Literal("http://localhost/accessible-generated")))
        )
        try {
            val prepared = withoutImplicitWriteIntent { ScriptExecutionPreparation(project).prepare(draft, null, null) }

            assertTrue(prepared.file.isValid)
            assertEquals(0, prepared.declarationIndex)
            assertEquals(
                "http://localhost/accessible-generated",
                (prepared.draft.request.target.url as ValueRef.Literal).value
            )
            assertTrue(File(prepared.file.path).readText().contains("/accessible-generated"))
            assertEquals(prepared.file.path, service.findDraft(draft.id)!!.bridge.generatedScriptPath)
        } finally {
            service.findDraft(draft.id)?.bridge?.generatedScriptPath?.let { File(it).delete() }
            service.deleteDraft(draft.id)
        }
    }

    fun testImportedRequestSavesPendingEditsFromAccessibilityCallbackWithoutImplicitWriteIntent() {
        val file =
            myFixture.addFileToProject("accessible-source.kttp.kts", "GET(\"http://localhost/original\")") as KtFile
        val selected = file.toKttpScript()!!.findRequestDeclarations().single()
        val draft = RequestEditorProjectionLoader(project).load(selected, file.virtualFile, 0)
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile)!!
        WriteCommandAction.runWriteCommandAction(project) {
            val start = document.text.indexOf("/original")
            document.replaceString(start, start + "/original".length, "/accessible-imported")
        }
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document))

        val prepared = withoutImplicitWriteIntent {
            ScriptExecutionPreparation(project).prepare(
                draft,
                file.virtualFile,
                selected
            )
        }

        assertEquals(file.virtualFile, prepared.file)
        assertEquals(0, prepared.declarationIndex)
        assertEquals(
            "http://localhost/accessible-imported",
            (prepared.draft.request.target.url as ValueRef.Literal).value
        )
        assertEquals(document.text, File(file.virtualFile.path).readText())
        assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document))
    }

    fun testMovedRequestUsesCurrentIndexAndSavesUnsavedContent() {
        val file = myFixture.addFileToProject(
            "move.kttp.kts",
            "GET(\"http://localhost/first\")\nPOST(\"http://localhost/selected\")\n"
        ) as KtFile
        val selected = file.toKttpScript()!!.findRequestDeclarations()[1]
        val draft = RequestEditorProjectionLoader(project).load(selected, file.virtualFile, 1)
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile)!!
        WriteCommandAction.runWriteCommandAction(project) {
            document.insertString(0, "DELETE(\"http://localhost/new\")\n")
            val old = document.text.indexOf("/selected")
            document.replaceString(old, old + "/selected".length, "/updated")
        }
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document))
        val prepared = ScriptExecutionPreparation(project).prepare(draft, file.virtualFile, selected)
        assertEquals(2, prepared.declarationIndex)
        assertEquals(
            "http://localhost/updated",
            (prepared.draft.request.target.url as io.github.photowey.kttp.idea.gui.ValueRef.Literal).value
        )
        assertEquals(document.text, File(file.virtualFile.path).readText())
        assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document))
    }

    fun testDeletedRequestCannotFallBackToItsOldIndexOrWholeFile() {
        val file = myFixture.addFileToProject(
            "deleted.kttp.kts",
            "GET(\"http://localhost/first\")\nPOST(\"http://localhost/selected\")\n"
        ) as KtFile
        val selected = file.toKttpScript()!!.findRequestDeclarations()[1]
        val draft = RequestEditorProjectionLoader(project).load(selected, file.virtualFile, 1)
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile)!!
        WriteCommandAction.runWriteCommandAction(project) {
            document.deleteString(document.text.indexOf("POST"), document.textLength)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
        val failure = runCatching {
            ScriptExecutionPreparation(project).prepare(
                draft,
                file.virtualFile,
                selected
            )
        }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure!!.message.orEmpty().contains("removed"))
    }

    fun testIndependentDraftNeverRunsItsStaleSourceLink() {
        val old = myFixture.addFileToProject("old-link.kttp.kts", "GET(\"http://localhost/old\")")
        val service = project.getService(GuiDraftService::class.java)
        val draft = service.createEmptyDraft().copy(
            request = io.github.photowey.kttp.idea.gui.RequestSpec(
                target = io.github.photowey.kttp.idea.gui.RequestTarget(
                    io.github.photowey.kttp.idea.gui.ValueRef.Literal(
                        "http://localhost/current"
                    )
                )
            ),
            bridge = io.github.photowey.kttp.idea.gui.ScriptBridgeState(sourceScriptPath = old.virtualFile.path)
        )
        val prepared = ScriptExecutionPreparation(project).prepare(draft, null, null)
        try {
            assertFalse(old.virtualFile.path == prepared.file.path)
            assertTrue(File(prepared.file.path).readText().contains("/current"))
            assertEquals("GET(\"http://localhost/old\")", old.text)
            assertEquals(0, prepared.declarationIndex)
        } finally {
            service.deleteDraft(draft.id)
            File(prepared.file.path).delete()
        }
    }

    private fun <T> withoutImplicitWriteIntent(action: () -> T): T {
        val application = ApplicationManager.getApplication()
        var result: Result<T>? = null
        requireNotNull(application.threadingSupport).releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
            assertTrue(application.isDispatchThread)
            assertFalse(
                "Simulate an accessibility callback without IDEA's input-event lock",
                application.isWriteIntentLockAcquired
            )
            KttpUiActions.submit(project) { result = runCatching(action) }
            assertNull("The model operation must be deferred outside the accessibility callback", result)
        }
        PlatformTestUtil.waitWithEventsDispatching("Write-safe UI operation completed", { result != null }, 10)
        return requireNotNull(result).getOrThrow()
    }
}
