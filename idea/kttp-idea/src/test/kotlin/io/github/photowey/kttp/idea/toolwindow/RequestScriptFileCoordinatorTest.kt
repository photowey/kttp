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
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import io.github.photowey.kttp.idea.KttpUiActions
import io.github.photowey.kttp.idea.gui.GuiDraftService
import io.github.photowey.kttp.idea.gui.RequestSpec
import io.github.photowey.kttp.idea.gui.RequestTarget
import io.github.photowey.kttp.idea.gui.ValueRef
import java.io.File

/**
 * Verifies script generation and updates preserve editor changes and operate under valid write access.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/06
 */
class RequestScriptFileCoordinatorTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()

    fun testGenerateFromAccessibilityCallbackWithoutImplicitWriteIntent() {
        val generated = withoutImplicitWriteIntent { RequestScriptFileCoordinator(project).generate(draft()) }

        assertTrue(generated.isValid)
        assertTrue(File(generated.path).readText().contains("http://localhost/accessible"))
    }

    fun testAppendToNewScriptFromAccessibilityCallbackWithoutImplicitWriteIntent() {
        val appended = withoutImplicitWriteIntent { RequestScriptFileCoordinator(project).append(draft(), null) }

        assertTrue(appended.isValid)
        assertTrue(File(appended.path).readText().contains("http://localhost/accessible"))
    }

    fun testAppendToExistingScriptFromAccessibilityCallbackWithoutImplicitWriteIntent() {
        val original = "GET(\"http://localhost/original\")\n"
        val target = myFixture.addFileToProject("append-accessible.kttp.kts", original).virtualFile

        val appended = withoutImplicitWriteIntent { RequestScriptFileCoordinator(project).append(draft(), target) }

        assertEquals(target, appended)
        val content = File(appended.path).readText()
        assertTrue(content.startsWith(original))
        assertTrue(content.contains("http://localhost/accessible"))
    }

    fun testOpenScriptFromAccessibilityCallbackWithoutImplicitWriteIntent() {
        val target =
            myFixture.addFileToProject("open-accessible.kttp.kts", "GET(\"http://localhost/accessible\")").virtualFile

        withoutImplicitWriteIntent { RequestScriptFileCoordinator(project).open(target.path) }

        PlatformTestUtil.waitWithEventsDispatching(
            "Script opened after background resolution",
            { FileEditorManager.getInstance(project).isFileOpen(target) }, 10
        )
    }

    private fun draft() = project.getService(GuiDraftService::class.java).createEmptyDraft().copy(
        request = RequestSpec(target = RequestTarget(ValueRef.Literal("http://localhost/accessible")))
    )

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
