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

package io.github.photowey.kttp.idea.importing.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import io.github.photowey.kttp.idea.env.KttpEnvironmentService
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.photowey.kttp.idea.importing.ImportCommitResult
import io.github.photowey.kttp.idea.importing.ImportDestination
import java.util.concurrent.CompletableFuture

/**
 * Exercises source restoration from a Swing completion context without an implicit write-safe transaction.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/14
 */
class ImportDialogCompletionTest : BasePlatformTestCase() {
    fun testCompletionSchedulesSourceRestorationInWriteSafeContext() {
        myFixture.configureByText("restored.kttp.kts", "// Original\n")
        val document = myFixture.editor.document
        val completed = CompletableFuture<Result<Unit>>()
        val dialog = RequestImportDialog(project, ImportDestination.Drafts, {
            completed.complete(runCatching {
                WriteCommandAction.runWriteCommandAction(project) { document.setText("// Imported\n") }
                PsiDocumentManager.getInstance(project).commitDocument(document)
            })
        })
        Disposer.register(testRootDisposable, dialog.disposable)
        val app = ApplicationManager.getApplication()
        requireNotNull(app.threadingSupport)
            .releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
                app.invokeLater({
                    dialog.completeImport(
                        ImportCommitResult(emptyList(), emptyList(), emptyList()),
                        ModalityState.nonModal()
                    )
                }, ModalityState.any())
                PlatformTestUtil.waitWithEventsDispatching("Imported source restored", { completed.isDone }, 10)
            }
        completed.get().getOrThrow()
        assertEquals("// Imported\n", document.text)
    }

    fun testSuccessfulImportSelectsSoleCreatedProfileBeforeRestoringRequests() {
        val service = KttpEnvironmentService.getInstance(project)
        service.setSelectedEnvironment(null)
        val observed = CompletableFuture<String?>()
        val oldDialog = TestDialogManager.setTestDialog(TestDialog { Messages.OK })
        val dialog = RequestImportDialog(project, ImportDestination.Drafts, {
            observed.complete(service.selectedEnvironment)
        })
        Disposer.register(testRootDisposable, dialog.disposable)
        try {
            dialog.completeImport(
                ImportCommitResult(emptyList(), emptyList(), listOf("dev-2")),
                ModalityState.nonModal()
            )
            PlatformTestUtil.waitWithEventsDispatching("Imported environment selected", { observed.isDone }, 10)
            assertEquals("dev-2", observed.get())
        } finally {
            service.setSelectedEnvironment(null)
            TestDialogManager.setTestDialog(oldDialog)
        }
    }

}
