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

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import java.util.concurrent.Callable

/**
 * Verifies append-only imports use a stable editor snapshot and valid top-level placement.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
class ImportCommitTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()
    fun testInsertsAfterContainingDeclarationWithoutReplacingSelection() {
        val original = "GET(\"https://example.com/original\") {}\n"
        myFixture.configureByText("target.kttp.kts", original)
        val editor = myFixture.editor
        editor.caretModel.moveToOffset(10)
        editor.selectionModel.setSelection(0, 3)
        val target = ImportDestination.Script(editor, myFixture.file.virtualFile, original)
        val bundle =
            ImportBundle(requests = listOf(ImportedRequest("New", "GET", ImportValue("https://example.com/imported"))))
        RequestImportCoordinator(project).commit(bundle, setOf(0), target, null)
        assertTrue(editor.document.text.startsWith(original.trimEnd()))
        assertTrue(editor.document.text.contains("https://example.com/imported"))
    }

    fun testCaretInsideImportOrHeaderUsesSafeTopLevelBoundary() {
        val original = "// Header comment\nimport kotlin.text.Regex\n\nGET(\"https://example.com/original\") {}\n"
        for (offset in listOf(5, original.indexOf("kotlin") + 3)) {
            myFixture.configureByText("header$offset.kttp.kts", original)
            myFixture.editor.caretModel.moveToOffset(offset)
            val target = ImportDestination.Script(myFixture.editor, myFixture.file.virtualFile, original)
            val bundle =
                ImportBundle(requests = listOf(ImportedRequest("New", "GET", ImportValue("https://example.com/new"))))
            RequestImportCoordinator(project).commit(bundle, setOf(0), target, null)
            assertTrue(myFixture.editor.document.text.startsWith("// Header comment\nimport kotlin.text.Regex\n"))
            assertFalse(myFixture.editor.document.text.contains("kott"))
        }
    }

    fun testInsertionWaitsForReusedEnvironmentBindingInitialization() {
        val original = "// Header\nval `url`: String by env\nGET(\"https://example.com/original\") {}\n"
        myFixture.configureByText("bindings.kttp.kts", original)
        myFixture.editor.caretModel.moveToOffset(0)
        val target = ImportDestination.Script(myFixture.editor, myFixture.file.virtualFile, original)
        val bundle = ImportBundle(requests = listOf(ImportedRequest("Imported", "GET", ImportValue("{{url}}", true))))
        RequestImportCoordinator(project).commit(bundle, setOf(0), target, null)
        val source = myFixture.editor.document.text
        assertTrue(source, source.indexOf("val `url`") < source.indexOf("GET(`url`"))
        assertEquals(1, Regex("val `url`: String by env").findAll(source).count())
        assertTrue(source.contains("https://example.com/original"))
    }

    fun testEnvironmentOnlyImportLeavesScriptBytesUnchanged() {
        val original = "// Original script\nGET(\"https://example.com/original\") {}\n"
        myFixture.configureByText("environment-only.kttp.kts", original)
        val target = ImportDestination.Script(myFixture.editor, myFixture.file.virtualFile, original)
        val bundle = ImportBundle(environments = listOf(ImportedEnvironment("dev", mapOf("host" to "example.com"))))
        val result = RequestImportCoordinator(project).commit(bundle, emptySet(), target, null)
        assertEquals(original, myFixture.editor.document.text)
        assertFalse(result.files.contains(myFixture.file.virtualFile))
        assertEquals(listOf("dev"), result.environmentProfiles)
        assertNotNull(myFixture.file.virtualFile.parent.findChild("kttp.env.json"))
    }

    fun testBackgroundCommitWritesScriptAndEnvironmentInSafeContext() {
        val original = "// Existing script\n"
        myFixture.configureByText("background.kttp.kts", original)
        myFixture.addFileToProject("kttp.env.json", "{\"dev\":{\"host\":\"existing\"}}")
        val target = ImportDestination.Script(myFixture.editor, myFixture.file.virtualFile, original)
        val bundle = ImportBundle(
            requests = listOf(ImportedRequest("New", "GET", ImportValue("https://example.com/imported"))),
            environments = listOf(ImportedEnvironment("dev", mapOf("host" to "imported")))
        )
        val app = ApplicationManager.getApplication()
        val result = requireNotNull(app.threadingSupport)
            .releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
                val future = app.executeOnPooledThread(Callable {
                    RequestImportCoordinator(project).commit(bundle, setOf(0), target, null)
                })
                PlatformTestUtil.waitWithEventsDispatching("Background import committed", { future.isDone }, 10)
                future.get()
            }
        assertEquals(listOf("dev-2"), result.environmentProfiles)
        assertTrue(myFixture.editor.document.text.contains("https://example.com/imported"))
        val environment = myFixture.file.virtualFile.parent.findChild("kttp.env.json")!!
        assertTrue(
            com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
                .getDocument(environment)!!.text.contains("imported")
        )
    }

    fun testEachLinkedDraftDeclaresItsOwnEnvironmentDelegates() {
        val bundle = ImportBundle(requests = listOf("First", "Second").map {
            ImportedRequest(it, "CUSTOM", ImportValue("{{url}}", true))
        })
        val result = RequestImportCoordinator(project).commit(bundle, setOf(0, 1), ImportDestination.Drafts, null)
        try {
            assertEquals(2, result.files.size)
            result.files.forEach { file ->
                val source = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file)!!.text
                assertTrue(source, source.contains("val `url`: String by env"))
            }
        } finally {
            result.drafts.forEach {
                project.getService(io.github.photowey.kttp.idea.gui.GuiDraftService::class.java).deleteDraft(it.id)
            }
            WriteCommandAction.runWriteCommandAction(project) { result.files.forEach { it.delete(this) } }
        }
    }

    fun testGuiPreviewKeepsIndependentScriptFileScopes() {
        val bundle = ImportBundle(requests = listOf("First", "Second").map {
            ImportedRequest(it, "CUSTOM", ImportValue("{{url}}", true))
        })
        val preview = RequestImportCoordinator(project).preview(bundle, setOf(0, 1), null)
        assertEquals(2, Regex("val `url`: String by env").findAll(preview).count())
    }

    fun testChangedEditorSnapshotCannotBeOverwritten() {
        myFixture.configureByText("changed.kttp.kts", "GET(\"https://example.com/old\")")
        val editor = myFixture.editor
        val target = ImportDestination.Script(editor, myFixture.file.virtualFile, editor.document.text)
        WriteCommandAction.runWriteCommandAction(project) { editor.document.setText("// user change\n") }
        val bundle =
            ImportBundle(requests = listOf(ImportedRequest("New", "GET", ImportValue("https://example.com/new"))))
        assertTrue(runCatching { RequestImportCoordinator(project).commit(bundle, setOf(0), target, null) }.isFailure)
        assertEquals("// user change\n", editor.document.text)
    }

    fun testFatalDiagnosticsPreventAllWrites() {
        myFixture.configureByText("invalid.kttp.kts", "// preserved\n")
        val target =
            ImportDestination.Script(myFixture.editor, myFixture.file.virtualFile, myFixture.editor.document.text)
        val bundle =
            ImportBundle(diagnostics = listOf(ImportDiagnostic(ImportSeverity.ERROR, "input", "Invalid input")))
        assertTrue(runCatching { RequestImportCoordinator(project).commit(bundle, emptySet(), target, null) }.isFailure)
        assertEquals("// preserved\n", myFixture.editor.document.text)
    }

    fun testScriptImportCanBeUndoneAsOneCommand() {
        val original = "// original\n"
        myFixture.configureByText("undo.kttp.kts", original)
        val target = ImportDestination.Script(myFixture.editor, myFixture.file.virtualFile, original)
        val bundle =
            ImportBundle(requests = listOf(ImportedRequest("New", "GET", ImportValue("https://example.com/new"))))
        RequestImportCoordinator(project).commit(bundle, setOf(0), target, null)
        myFixture.performEditorAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_UNDO)
        assertEquals(original, myFixture.editor.document.text)
    }

    fun testWriterFailureRestoresEnvironmentAndRemovesOnlyCreatedFiles() {
        val original = "{\"dev\":{\"host\":\"existing\"}}"
        val env = myFixture.addFileToProject("rollback/kttp.env.json", original).virtualFile
        val directory = env.parent
        var created: com.intellij.openapi.vfs.VirtualFile? = null
        val failure = runCatching {
            ImportTargetWriter(project).write {
                environment(directory, "{\"new\":{}}")
                created = create(directory, ".kttp.kts", "GET(\"https://example.com\")")
                save()
                error("Simulated later persistence failure")
            }
        }
        assertTrue(failure.isFailure)
        assertFalse(created!!.isValid)
        assertEquals(
            original,
            com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(env)!!.text
        )
        assertEquals(original, java.io.File(env.path).readText())
    }

}
