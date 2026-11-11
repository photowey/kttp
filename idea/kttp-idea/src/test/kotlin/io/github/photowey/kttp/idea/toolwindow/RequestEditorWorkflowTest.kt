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

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sun.net.httpserver.HttpServer
import io.github.photowey.kttp.core.enums.HttpMethod
import io.github.photowey.kttp.idea.execution.KttpExecutionResult
import io.github.photowey.kttp.idea.execution.RequestExecutionMode
import io.github.photowey.kttp.idea.gui.*
import io.github.photowey.kttp.idea.path.KttpProjectPath
import io.github.photowey.kttp.idea.script.toKttpScript
import java.awt.Component
import java.awt.Container
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JButton
import javax.swing.JTable
import javax.swing.JTextField

/**
 * Verifies draft saving, script updates, request input preservation, and asynchronous execution state.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class RequestEditorWorkflowTest : BasePlatformTestCase() {
    fun testSavePreservesPathRepeatedHeadersAndFormValues() {
        val spec = RequestSpec(
            method = HttpMethod.POST,
            target = RequestTarget(
                ValueRef.Literal("http://localhost/orders/{id}"), listOf(NameValueRef("id", ValueRef.Literal("42"))),
                listOf(NameValueRef("tag", ValueRef.Literal("a")), NameValueRef("tag", ValueRef.Literal("b")))
            ),
            headers = listOf(HeaderSpec("X-Tag", ValueRef.Literal("a")), HeaderSpec("X-Tag", ValueRef.Literal("b"))),
            auth = AuthSpec.ApiKey("api-key", ValueRef.Literal("test-only"), ApiKeyPlacement.QUERY),
            body = BodySpec.FormUrlEncoded(listOf(NameValueRef("note", ValueRef.Literal(" a&b=+% "))))
        )
        val draft = draft(spec)
        val editor = RequestEditorPanel(project) { _, _ -> }
        editor.loadDraft(draft)
        click(editor, "Save Draft")
        assertEquals(spec, project.getService(GuiDraftService::class.java).findDraft(draft.id)!!.request)
    }

    fun testSaveIncludesCurrentlyEditedCell() {
        val draft = draft(RequestSpec(target = RequestTarget(ValueRef.Literal("http://localhost/"))))
        val editor = RequestEditorPanel(project) { _, _ -> }
        editor.loadDraft(draft)
        val params = descendants(editor).filterIsInstance<ParamsTabPanel>().single()
        val table = descendants(params).filterIsInstance<JTable>().single()
        table.editCellAt(0, 0)
        (table.editorComponent as JTextField).text = "q"
        table.cellEditor.stopCellEditing()
        table.editCellAt(0, 1)
        (table.editorComponent as JTextField).text = "just typed"
        click(editor, "Save Draft")
        val saved = project.getService(GuiDraftService::class.java).findDraft(draft.id)!!
        assertEquals(listOf(NameValueRef("q", ValueRef.Literal("just typed"))), saved.request.target.queryParams)
    }

    fun testLocalFileSettingsSurviveSave() {
        val spec = RequestSpec(
            method = HttpMethod.POST, target = RequestTarget(ValueRef.Literal("http://localhost/")),
            body = BodySpec.Raw(ValueRef.LocalFile("payload.txt", "UTF-16LE", false), ValueRef.Literal("text/plain"))
        )
        val draft = draft(spec)
        val editor = RequestEditorPanel(project) { _, _ -> }
        editor.loadDraft(draft)
        click(editor, "Save Draft")
        assertEquals(spec.body, project.getService(GuiDraftService::class.java).findDraft(draft.id)!!.request.body)
    }

    fun testUpdateScriptPreservesUnsavedEditorChanges() {
        val directory = Files.createTempDirectory("kttp-managed-script")
        val file = directory.resolve("managed.kttp.kts").toFile()
        val original = "GET(\"http://localhost/original\")\n"
        file.writeText(original)
        val service = project.getService(GuiDraftService::class.java)
        val managed = service.markGenerated(
            draft(RequestSpec(target = RequestTarget(ValueRef.Literal("http://localhost/updated")))),
            file.path
        )
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)!!
        val document = FileDocumentManager.getInstance().getDocument(vf)!!
        WriteCommandAction.runWriteCommandAction(project) {
            document.insertString(
                document.textLength,
                "// unsaved user edit\n"
            )
        }
        val editor = RequestEditorPanel(project) { _, _ -> }
        editor.loadDraft(managed)
        val oldDialog = TestDialogManager.setTestDialog(TestDialog { Messages.OK })
        var generated: java.io.File? = null
        try {
            click(editor, "Update Script")
            val updated = service.findDraft(managed.id)!!
            generated = java.io.File(updated.bridge.generatedScriptPath!!)
            assertFalse("An edited document must force a separate generated script", file.path == generated.path)
            assertEquals(original, file.readText())
            assertEquals(original + "// unsaved user edit\n", document.text)
            assertTrue(generated.readText().contains("http://localhost/updated"))
        } finally {
            TestDialogManager.setTestDialog(oldDialog)
            FileDocumentManager.getInstance().reloadFromDisk(document)
            generated?.delete()
            file.delete()
            directory.toFile().delete()
        }
    }

    fun testSendReturnsPromptlyAndDoesNotReplaceNewlySelectedDraft() {
        val release = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            release.await(10, TimeUnit.SECONDS)
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.use { it.write("ok".toByteArray()) }
        }
        server.start()
        val response = AtomicReference<KttpExecutionResult>()
        val editor = RequestEditorPanel(project) { result, _ -> response.set(result) }
        editor.loadDraft(draft(RequestSpec(target = RequestTarget(ValueRef.Literal("http://127.0.0.1:${server.address.port}/")))))
        try {
            val started = System.nanoTime()
            click(editor, "Send")
            assertTrue("HTTP must not block the EDT", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1000)
            val next = draft(RequestSpec(target = RequestTarget(ValueRef.Literal("http://localhost/next"))))
            editor.loadDraft(next)
            release.countDown()
            PlatformTestUtil.waitWithEventsDispatching("HTTP callback", { response.get() != null }, 10)
            assertTrue(response.get().message, response.get().success)
            click(editor, "Save Draft")
            assertEquals(next.request, project.getService(GuiDraftService::class.java).findDraft(next.id)!!.request)
        } finally {
            release.countDown()
            server.stop(0)
        }
    }

    fun testSendCompletionKeepsScriptGeneratedWhileRequestWasRunning() {
        val release = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            release.await(10, TimeUnit.SECONDS)
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        val response = AtomicReference<KttpExecutionResult>()
        val editor = RequestEditorPanel(project) { result, _ -> response.set(result) }
        val draft =
            draft(RequestSpec(target = RequestTarget(ValueRef.Literal("http://127.0.0.1:${server.address.port}/"))))
        editor.loadDraft(draft)
        var generated: java.io.File? = null
        try {
            click(editor, "Send")
            click(editor, "Generate Script")
            val service = project.getService(GuiDraftService::class.java)
            val bridge = service.findDraft(draft.id)!!.bridge
            generated = java.io.File(bridge.generatedScriptPath!!)
            release.countDown()
            PlatformTestUtil.waitWithEventsDispatching("HTTP callback", { response.get() != null }, 10)
            assertTrue(descendants(editor).filterIsInstance<JButton>().single { it.text == "Open Script" }.isEnabled)
            assertTrue(descendants(editor).filterIsInstance<JButton>().any { it.text == "Update Script" })
            assertEquals(bridge, service.findDraft(draft.id)!!.bridge)
            assertNotNull(service.findDraft(draft.id)!!.meta.lastSentAt)
        } finally {
            release.countDown()
            server.stop(0)
            generated?.delete()
        }
    }

    fun testQueuedScriptRefreshDoesNotReplaceNewlySelectedDraft() {
        val main =
            MainContentPanel(project, ResponseTabPanel(project), HistoryTabPanel(project), AssertionsTabPanel(project))
        val editor = descendants(main).filterIsInstance<RequestEditorPanel>().single()
        val file = myFixture.addFileToProject("queued-change.kttp.kts", "GET(\"http://localhost/old\")").virtualFile
        editor.currentSourceFile = file
        editor.currentDeclarationIndex = 0
        main.onScriptFileChanged(file)
        val next = draft(RequestSpec(target = RequestTarget(ValueRef.Literal("http://localhost/next"))))
        main.loadDraft(next)
        com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
        assertNull("A queued refresh must not reattach the previous script", editor.currentSourceFile)
        click(editor, "Save Draft")
        assertEquals(next.request, project.getService(GuiDraftService::class.java).findDraft(next.id)!!.request)
    }

    fun testDetachSaveAndGeneratePreserveLiteralAuthAndBodySources() {
        val spec = RequestSpec(
            method = HttpMethod.POST,
            target = RequestTarget(ValueRef.Literal("http://localhost/\${env:HOST}")),
            headers = listOf(HeaderSpec("X-Literal", ValueRef.Literal("\${env:TOKEN}"))),
            auth = AuthSpec.Basic(ValueRef.Literal("  user  "), ValueRef.Literal("\${env:PASSWORD}")),
            body = BodySpec.Raw(ValueRef.LocalFile("payload.txt", "UTF-16LE", false), ValueRef.Literal("text/plain"))
        )
        val service = project.getService(GuiDraftService::class.java)
        val existing = service.listDrafts().map { it.id }.toSet()
        val editor = RequestEditorPanel(project) { _, _ -> }
        editor.loadDraft(draft(spec).copy(ownership = DraftOwnership.SCRIPT_IMPORTED))
        var generated: java.io.File? = null
        try {
            click(editor, "Detach")
            click(editor, "Save Draft")
            val saved = service.listDrafts().single { it.id !in existing }
            assertEquals(spec, saved.request)
            click(editor, "Generate Script")
            val updated = service.findDraft(saved.id)!!
            generated = java.io.File(updated.bridge.generatedScriptPath!!)
            assertEquals(spec, updated.request)
            assertTrue(generated.isFile)
        } finally {
            generated?.delete()
            service.listDrafts().filter { it.id !in existing }.forEach { service.deleteDraft(it.id) }
        }
    }

    fun testSavePreservesAuthAndBodyVariantsWithoutTrimmingValues() {
        val cases = listOf(
            AuthSpec.Bearer(ValueRef.Literal("  bearer token  ")) to BodySpec.Json(ValueRef.Literal("\${env:JSON}")),
            AuthSpec.ApiKey("key", ValueRef.Literal("  api value  "), ApiKeyPlacement.QUERY) to
                BodySpec.FormUrlEncoded(listOf(NameValueRef("field", ValueRef.Literal("\${env:FIELD}")))),
            AuthSpec.Basic(
                ValueRef.Env("USER"),
                ValueRef.Literal(" pass ")
            ) to BodySpec.Raw(ValueRef.Literal("@file:literal.txt"))
        )
        val service = project.getService(GuiDraftService::class.java)
        val editor = RequestEditorPanel(project) { _, _ -> }
        for ((auth, body) in cases) {
            val spec =
                RequestSpec(target = RequestTarget(ValueRef.Literal("http://localhost/")), auth = auth, body = body)
            val draft = draft(spec)
            try {
                editor.loadDraft(draft)
                click(editor, "Save Draft")
                assertEquals(spec, service.findDraft(draft.id)!!.request)
            } finally {
                service.deleteDraft(draft.id)
            }
        }
    }

    fun testScriptOnlyClientConfigurationCannotBeMadeSendableByDetach() {
        val script = myFixture.addFileToProject(
            "script-only-detach.kttp.kts", """
            configureClient { followRedirects(false) }
            GET("http://localhost/requires-configuration")
        """.trimIndent()
        )
        val declaration = requireNotNull(script.virtualFile.toKttpScript(project)).findRequestDeclarations().single()
        val editor = RequestEditorPanel(project) { _, _ -> }
        editor.loadFromRequest(declaration, script.virtualFile, 0)
        val header = descendants(editor).filterIsInstance<ComposerHeaderPanel>().single()
        val service = project.getService(GuiDraftService::class.java)
        val before = service.listDrafts().map { it.id }.toSet()
        assertFalse("An incomplete script projection must not be detachable", header.detachButton.isEnabled)
        header.detachButton.doClick()
        descendants(editor).filterIsInstance<ComposerHeaderPanel>().single().executionModeCombo.selectedItem =
            RequestExecutionMode.QUICK_REQUEST
        assertFalse("Detach must not silently make omitted client configuration sendable", header.sendButton.isEnabled)
        assertFalse(header.saveDraftButton.isEnabled)
        assertTrue(header.modeLabel.toolTipText.contains("source script"))
        assertEquals(before, service.listDrafts().map { it.id }.toSet())
    }

    fun testWorkingSessionCannotDetachAnIncompleteProjectionOutsideTheButton() {
        val imported = draft(RequestSpec(target = RequestTarget(ValueRef.Literal("http://localhost/")))).copy(
            ownership = DraftOwnership.SCRIPT_IMPORTED,
            bridge = ScriptBridgeState(
                mode = GuiMode.SCRIPT_ONLY, gaps = listOf(
                    SemanticGap(
                        GapKind.REQUEST_SHAPING_UNSUPPORTED,
                        message = "Client configuration requires script execution"
                    )
                )
            )
        )
        val session = EditorWorkingSession(imported)
        session.detach()
        assertEquals(
            "The session boundary must retain the incomplete projection and its explanation",
            imported,
            session.draft
        )
        assertFalse(session.editable)
    }

    fun testDetachAndSavePreserveExplicitEmptyAuthAndBodyVariants() {
        val cases = listOf(
            AuthSpec.Basic(ValueRef.Literal(""), ValueRef.Literal("")) to BodySpec.Json(ValueRef.Literal("")),
            AuthSpec.Bearer(ValueRef.Literal("")) to BodySpec.Raw(ValueRef.Literal(""), ValueRef.Literal("text/plain")),
            AuthSpec.ApiKey("X-Empty", ValueRef.Literal("")) to BodySpec.FormUrlEncoded(emptyList())
        )
        val service = project.getService(GuiDraftService::class.java)
        val existing = service.listDrafts().map { it.id }.toSet()
        val editor = RequestEditorPanel(project) { _, _ -> }
        try {
            for ((auth, body) in cases) {
                val spec =
                    RequestSpec(target = RequestTarget(ValueRef.Literal("http://localhost/")), auth = auth, body = body)
                val before = service.listDrafts().map { it.id }.toSet()
                editor.loadDraft(draft(spec).copy(ownership = DraftOwnership.SCRIPT_IMPORTED))
                click(editor, "Detach")
                click(editor, "Save Draft")
                val saved = service.listDrafts().single { it.id !in before }
                assertEquals("The selected empty variant is request semantics", spec, saved.request)
                editor.loadDraft(saved)
                click(editor, "Save Draft")
                assertEquals(spec, service.findDraft(saved.id)!!.request)
            }
        } finally {
            service.listDrafts().filter { it.id !in existing }.forEach { service.deleteDraft(it.id) }
        }
    }

    fun testFailedSaveKeepsTheWorkingFieldsForRetry() {
        val service = project.getService(GuiDraftService::class.java)
        val draft =
            service.saveDraft(draft(RequestSpec(target = RequestTarget(ValueRef.Literal("http://localhost/original")))))
        val editor = RequestEditorPanel(project) { _, _ -> }
        editor.loadDraft(draft)
        val header = descendants(editor).filterIsInstance<ComposerHeaderPanel>().single()
        header.urlField.text = "http://localhost/retry"
        descendants(editor).filterIsInstance<HeadersTabPanel>().single().setHeaderEntries(listOf("X-Retry" to "kept"))
        val archive = File(KttpProjectPath.getGuiStateDir(project.basePath!!), "drafts.json")
        val originalArchive = archive.readBytes()
        var reported = false
        val oldDialog = TestDialogManager.setTestDialog(TestDialog { reported = true; Messages.OK })
        try {
            archive.writeText("{ corrupt archive")
            click(editor, "Save Draft")
            assertTrue("Save errors must be presented without escaping the UI action", reported)
            assertEquals("http://localhost/retry", header.urlField.text)
            assertEquals("{ corrupt archive", archive.readText())
            assertEquals(draft.request, service.findDraft(draft.id)!!.request)
            archive.writeBytes(originalArchive)
            click(editor, "Save Draft")
            val saved = service.findDraft(draft.id)!!
            assertEquals(ValueRef.Literal("http://localhost/retry"), saved.request.target.url)
            assertEquals(listOf(HeaderSpec("X-Retry", ValueRef.Literal("kept"))), saved.request.headers)
        } finally {
            TestDialogManager.setTestDialog(oldDialog)
            archive.writeBytes(originalArchive)
            service.deleteDraft(draft.id)
        }
    }

    fun testSendCompletionPreservesEditsMadeWhileTheRequestRuns() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            entered.countDown()
            release.await(10, TimeUnit.SECONDS)
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        val response = AtomicReference<KttpExecutionResult>()
        val editor = RequestEditorPanel(project) { result, _ -> response.set(result) }
        val draft =
            draft(RequestSpec(target = RequestTarget(ValueRef.Literal("http://127.0.0.1:${server.address.port}/"))))
        editor.loadDraft(draft)
        try {
            click(editor, "Send")
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            descendants(editor).filterIsInstance<ComposerHeaderPanel>().single().urlField.text =
                "http://localhost/edited"
            descendants(editor).filterIsInstance<HeadersTabPanel>().single()
                .setHeaderEntries(listOf("X-Edited" to "after send"))
            release.countDown()
            PlatformTestUtil.waitWithEventsDispatching("HTTP callback", { response.get() != null }, 10)
            click(editor, "Save Draft")
            val saved = project.getService(GuiDraftService::class.java).findDraft(draft.id)!!
            assertEquals(ValueRef.Literal("http://localhost/edited"), saved.request.target.url)
            assertEquals(listOf(HeaderSpec("X-Edited", ValueRef.Literal("after send"))), saved.request.headers)
        } finally {
            release.countDown()
            server.stop(0)
        }
    }

    fun testSendCanBeCancelledWhileTheServerIsWaiting() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            entered.countDown()
            release.await(10, TimeUnit.SECONDS)
            exchange.close()
        }
        server.start()
        val response = AtomicReference<KttpExecutionResult>()
        val editor = RequestEditorPanel(project) { result, _ -> response.set(result) }
        editor.loadDraft(draft(RequestSpec(target = RequestTarget(ValueRef.Literal("http://127.0.0.1:${server.address.port}/")))))
        try {
            click(editor, "Send")
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val cancel = descendants(editor).filterIsInstance<JButton>().single { it.text == "Cancel" }
            assertTrue(cancel.isEnabled)
            cancel.doClick()
            PlatformTestUtil.waitWithEventsDispatching("cancelled HTTP callback", { response.get() != null }, 5)
            assertFalse(response.get().success)
            assertTrue(descendants(editor).filterIsInstance<JButton>().single { it.text == "Send" }.isEnabled)
        } finally {
            release.countDown()
            server.stop(0)
        }
    }

    private fun draft(spec: RequestSpec) =
        GuiDraft(UUID.randomUUID().toString(), "Workflow test", DraftOwnership.DETACHED, spec)

    private fun click(editor: RequestEditorPanel, text: String) {
        descendants(editor).filterIsInstance<JButton>().single { it.text == text }.doClick()
        com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
    }

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
    }
}
