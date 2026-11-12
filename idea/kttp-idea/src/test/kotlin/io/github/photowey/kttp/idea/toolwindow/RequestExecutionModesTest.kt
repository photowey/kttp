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
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.photowey.kttp.idea.execution.RequestExecutionMode
import io.github.photowey.kttp.idea.gui.GuiDraftService
import io.github.photowey.kttp.idea.gui.RequestExecutionPreferences
import io.github.photowey.kttp.idea.script.toKttpScript
import org.jetbrains.kotlin.psi.KtFile
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JComboBox

/**
 * Verifies GUI execution defaults, saved mode choices, switching, and detached-draft behavior.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class RequestExecutionModesTest : BasePlatformTestCase() {
    fun testCustomMethodImportsForFollowScriptWithoutFallingBackToANewDraft() {
        val source = "request(\"CUSTOM\", \"http://localhost/custom\") { header(\"Accept\", \"application/json\") }"
        val file = myFixture.addFileToProject("custom-mode.kttp.kts", source) as KtFile
        val declaration = file.toKttpScript()!!.findRequestDeclarations().single()
        val imported = RequestEditorProjectionLoader(project).load(declaration, file.virtualFile, 0)
        assertEquals(io.github.photowey.kttp.idea.gui.GuiMode.SCRIPT_ONLY, imported.bridge.mode)
        assertEquals(
            io.github.photowey.kttp.idea.gui.ValueRef.Literal("http://localhost/custom"),
            imported.request.target.url
        )
        val editor = RequestEditorPanel(project) { _, _ -> fail("Selecting must not send") }
        editor.loadFromRequest(declaration, file.virtualFile, 0)
        assertEquals(file.virtualFile, editor.currentSourceFile)
        assertEquals(RequestExecutionMode.FOLLOW_SCRIPT, modeCombo(editor).selectedItem)
        assertTrue(button(editor, "Send").isEnabled)
        val header = descendants(editor).filterIsInstance<ComposerHeaderPanel>().single()
        assertEquals("Script", header.methodComboBox.selectedItem)
        modeCombo(editor).selectedItem = RequestExecutionMode.QUICK_REQUEST
        assertFalse(button(editor, "Send").isEnabled)
        assertEquals(source, file.text)
    }

    fun testImportAndRefreshAcquireReadAccessForUiCallbacks() {
        val file = myFixture.addFileToProject(
            "callback-mode.kttp.kts", "GET(\"http://localhost/callback\", name = \"Callback request\")"
        ) as KtFile
        val declaration = file.toKttpScript()!!.findRequestDeclarations().single()
        val editor = RequestEditorPanel(project) { _, _ -> fail("Importing must not send") }
        val application = ApplicationManager.getApplication()
        requireNotNull(application.threadingSupport)
            .releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
                assertFalse(application.isReadAccessAllowed)
                val imported = RequestEditorProjectionLoader(project).load(declaration, file.virtualFile, 0)
                assertEquals("Callback request", imported.title)
                editor.loadFromRequest(declaration, file.virtualFile, 0)
                editor.refreshCurrentSource()
                assertTrue(button(editor, "Send").isEnabled)
            }
    }

    fun testNewDraftDefaultsToQuickAndChoiceIsSaved() {
        val service = project.getService(GuiDraftService::class.java)
        val draft = service.createEmptyDraft()
        val editor = RequestEditorPanel(project) { _, _ -> fail("Switching mode must not send") }
        editor.loadDraft(draft)
        val combo = modeCombo(editor)
        assertEquals(RequestExecutionMode.QUICK_REQUEST, combo.selectedItem)
        combo.selectedItem = RequestExecutionMode.FOLLOW_SCRIPT
        button(editor, "Save Draft").doClick()
        PlatformTestUtil.waitWithEventsDispatching(
            "execution mode is saved",
            { service.findDraft(draft.id)?.executionMode == RequestExecutionMode.FOLLOW_SCRIPT },
            10
        )
        assertEquals(RequestExecutionMode.FOLLOW_SCRIPT, service.findDraft(draft.id)!!.executionMode)
        service.deleteDraft(draft.id)
    }

    fun testDynamicSourceDefaultsToFollowAndQuickExplainsItsRestriction() {
        val file = myFixture.addFileToProject(
            "dynamic-mode.kttp.kts",
            "fun target() = \"http://localhost/\"\nGET(target())"
        ) as KtFile
        val declaration = file.toKttpScript()!!.findRequestDeclarations().single()
        val editor = RequestEditorPanel(project) { _, _ -> fail("No send expected") }
        editor.loadFromRequest(declaration, file.virtualFile, 0)
        assertEquals(RequestExecutionMode.FOLLOW_SCRIPT, modeCombo(editor).selectedItem)
        assertTrue(button(editor, "Send").isEnabled)
        modeCombo(editor).selectedItem = RequestExecutionMode.QUICK_REQUEST
        assertFalse(button(editor, "Send").isEnabled)
        assertTrue(button(editor, "Send").toolTipText.isNotBlank())
        editor.loadFromRequest(declaration, file.virtualFile, 0)
        assertEquals(RequestExecutionMode.QUICK_REQUEST, modeCombo(editor).selectedItem)
    }

    fun testModeSwitchLeavesScriptContentAndReadonlyOwnershipUnchanged() {
        val text = "GET(\"http://localhost/\")"
        val file = myFixture.addFileToProject("readonly-mode.kttp.kts", text) as KtFile
        val editor = RequestEditorPanel(project) { _, _ -> fail("No send expected") }
        editor.loadFromRequest(file.toKttpScript()!!.findRequestDeclarations().single(), file.virtualFile, 0)
        modeCombo(editor).selectedItem = RequestExecutionMode.QUICK_REQUEST
        assertEquals(text, file.text)
        assertFalse(button(editor, "Save Draft").isEnabled)
        assertFalse(button(editor, "Generate Script").isEnabled)
        assertTrue(button(editor, "Send").isEnabled)
    }

    fun testDetachShowsQuickModeAndKeepsTheImportedRequestPreference() {
        val text = "GET(\"http://localhost/detached\").then { assertStatus(200) }"
        val file = myFixture.addFileToProject("detach-mode.kttp.kts", text) as KtFile
        val declaration = file.toKttpScript()!!.findRequestDeclarations().single()
        val editor = RequestEditorPanel(project) { _, _ -> fail("Detach must not send") }
        editor.loadFromRequest(declaration, file.virtualFile, 0)
        assertEquals(RequestExecutionMode.FOLLOW_SCRIPT, modeCombo(editor).selectedItem)

        button(editor, "Detach").doClick()

        assertEquals(RequestExecutionMode.QUICK_REQUEST, modeCombo(editor).selectedItem)
        assertTrue(button(editor, "Send").toolTipText.contains("directly"))
        assertTrue(button(editor, "Save Draft").isEnabled)
        assertNull(editor.currentSourceFile)
        assertEquals(text, file.text)
        editor.loadFromRequest(declaration, file.virtualFile, 0)
        assertEquals(RequestExecutionMode.FOLLOW_SCRIPT, modeCombo(editor).selectedItem)
    }

    fun testDeletingSelectedSourceDisablesSendWhenInvokedBeforeRefresh() {
        val file = myFixture.addFileToProject("delete-before-refresh.kttp.kts", "GET(\"http://localhost/\")") as KtFile
        val editor = RequestEditorPanel(project) { _, _ -> fail("Deleted source must not execute") }
        editor.loadFromRequest(file.toKttpScript()!!.findRequestDeclarations().single(), file.virtualFile, 0)
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file.virtualFile)!!
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            document.setText("")
            com.intellij.psi.PsiDocumentManager.getInstance(project).commitDocument(document)
        }
        button(editor, "Send").doClick(0)
        assertFalse("Send should explain that the source was removed", button(editor, "Send").isEnabled)
        assertTrue(button(editor, "Send").toolTipText.contains("unavailable"))
    }

    fun testRejectedModeSwitchPreservesThePendingCellEditor() {
        val editor = RequestEditorPanel(project) { _, _ -> fail("Mode switch must not send") }
        val table = descendants(descendants(editor).filterIsInstance<ParamsTabPanel>().single())
            .filterIsInstance<javax.swing.JTable>().single()
        table.columnModel.getColumn(1).cellEditor = object : javax.swing.DefaultCellEditor(javax.swing.JTextField()) {
            override fun stopCellEditing(): Boolean = false
        }
        assertTrue(table.editCellAt(0, 1))
        val pending = table.editorComponent as javax.swing.JTextField
        pending.text = "pending invalid value"
        modeCombo(editor).selectedItem = RequestExecutionMode.FOLLOW_SCRIPT
        assertEquals(RequestExecutionMode.QUICK_REQUEST, modeCombo(editor).selectedItem)
        assertTrue("A rejected switch must keep the pending editor", table.isEditing)
        assertEquals("pending invalid value", (table.editorComponent as javax.swing.JTextField).text)
        table.cellEditor.cancelCellEditing()
    }

    fun testInsertingRequestDoesNotTransferAnotherRequestsQuickPreference() {
        val file = myFixture.addFileToProject(
            "stable-mode-identity.kttp.kts",
            "GET(\"http://localhost/first\")\nGET(\"http://localhost/second\")"
        ) as KtFile
        val editor = RequestEditorPanel(project) { _, _ -> fail("No execution expected") }
        editor.loadFromRequest(file.toKttpScript()!!.findRequestDeclarations()[0], file.virtualFile, 0)
        modeCombo(editor).selectedItem = RequestExecutionMode.QUICK_REQUEST
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file.virtualFile)!!
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            document.insertString(0, "POST(\"http://localhost/new\")\n")
            com.intellij.psi.PsiDocumentManager.getInstance(project).commitDocument(document)
        }
        val declarations = file.toKttpScript()!!.findRequestDeclarations()
        editor.loadFromRequest(declarations[0], file.virtualFile, 0)
        assertEquals(
            "New requests must retain the safe script default",
            RequestExecutionMode.FOLLOW_SCRIPT,
            modeCombo(editor).selectedItem
        )
        editor.loadFromRequest(declarations[1], file.virtualFile, 1)
        assertEquals(
            "Moved request should retain its own choice",
            RequestExecutionMode.QUICK_REQUEST,
            modeCombo(editor).selectedItem
        )
    }

    fun testInfixAndParenthesizedCallbacksHaveDistinctModeIdentities() {
        for ((name, original, added) in listOf(
            Triple(
                "infix", "GET(\"http://localhost/same\") thenResponse { println(\"original\") }",
                "GET(\"http://localhost/same\") thenResponse { println(\"new\") }"
            ),
            Triple(
                "wrapped", "(GET(\"http://localhost/same\")).then { println(\"original\") }",
                "(GET(\"http://localhost/same\")).then { println(\"new\") }"
            ),
            Triple(
                "qualified", "builder.GET(\"http://localhost/same\").then { println(\"original\") }",
                "builder.GET(\"http://localhost/same\").then { println(\"new\") }"
            )
        )) {
            val file = myFixture.addFileToProject("$name.kttp.kts", original) as KtFile
            val editor = RequestEditorPanel(project) { _, _ -> fail("No execution expected") }
            editor.loadFromRequest(file.toKttpScript()!!.findRequestDeclarations().single(), file.virtualFile, 0)
            modeCombo(editor).selectedItem = RequestExecutionMode.QUICK_REQUEST
            val document =
                com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file.virtualFile)!!
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                document.insertString(0, "$added\n")
                com.intellij.psi.PsiDocumentManager.getInstance(project).commitDocument(document)
            }
            val declarations = file.toKttpScript()!!.findRequestDeclarations()
            editor.loadFromRequest(declarations[0], file.virtualFile, 0)
            assertEquals(name, RequestExecutionMode.FOLLOW_SCRIPT, modeCombo(editor).selectedItem)
            editor.loadFromRequest(declarations[1], file.virtualFile, 1)
            assertEquals(name, RequestExecutionMode.QUICK_REQUEST, modeCombo(editor).selectedItem)
        }
    }

    fun testIdenticalDuplicateInsertionInvalidatesAmbiguousPreferences() {
        val request = "GET(\"http://localhost/same\").then { println(\"same\") }"
        val file = myFixture.addFileToProject("duplicates.kttp.kts", "$request\n$request") as KtFile
        val editor = RequestEditorPanel(project) { _, _ -> fail("No execution expected") }
        editor.loadFromRequest(file.toKttpScript()!!.findRequestDeclarations()[0], file.virtualFile, 0)
        modeCombo(editor).selectedItem = RequestExecutionMode.QUICK_REQUEST
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file.virtualFile)!!
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            document.insertString(0, "$request\n")
            com.intellij.psi.PsiDocumentManager.getInstance(project).commitDocument(document)
        }
        editor.loadFromRequest(file.toKttpScript()!!.findRequestDeclarations()[0], file.virtualFile, 0)
        assertEquals(RequestExecutionMode.FOLLOW_SCRIPT, modeCombo(editor).selectedItem)
    }

    private fun modeCombo(editor: RequestEditorPanel): JComboBox<*> {
        val combo = descendants(editor).filterIsInstance<JComboBox<*>>().firstOrNull {
            it.itemCount > 0 && it.getItemAt(0) is RequestExecutionMode
        }
        assertNotNull("Execution mode must be a visible native control", combo)
        return combo!!
    }

    private fun button(root: Component, text: String) =
        descendants(root).filterIsInstance<JButton>().single { it.text == text }

    private fun descendants(root: Component): List<Component> = listOf(root) +
        if (root is Container) root.components.flatMap(::descendants) else emptyList()
}
