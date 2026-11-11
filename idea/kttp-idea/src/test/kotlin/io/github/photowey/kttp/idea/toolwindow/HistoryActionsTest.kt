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
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.photowey.kttp.idea.io.HistoryPersistence
import java.awt.event.MouseEvent
import java.io.File
import java.nio.file.Files
import javax.swing.JList

/**
 * Verifies history double-click actions and safe handling of missing or newly created response files.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/06
 */
class HistoryActionsTest : BasePlatformTestCase() {
    fun testDoubleClickOpensAResponseCreatedOutsideVfs() = withFreshResponse { panel, list, file ->
        click(list, 2)

        waitForOpenFile(file)
        assertTrue(FileEditorManager.getInstance(project).openFiles.any { it.path == file.path })
        assertEquals(file.absolutePath, panel.getSelectedResponseFilePath())
        assertEquals(file.absolutePath, HistoryPersistence.load().last().responseFilePath)
    }

    fun testDoubleClickOpensFreshResponseWithoutImplicitWriteIntent() = withFreshResponse { _, list, file ->
        val application = ApplicationManager.getApplication()
        requireNotNull(application.threadingSupport).releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
            assertTrue(application.isDispatchThread)
            assertFalse(application.isWriteIntentLockAcquired)
            click(list, 2)
        }

        waitForOpenFile(file)
        assertTrue(FileEditorManager.getInstance(project).openFiles.any { it.path == file.path })
    }

    fun testSingleClickAndUnselectedDoubleClickDoNotOpenResponses() = withFreshResponse { _, list, file ->
        click(list, 1)
        assertFalse(FileEditorManager.getInstance(project).openFiles.any { it.path == file.path })
        list.clearSelection()

        click(list, 2)

        assertFalse(FileEditorManager.getInstance(project).openFiles.any { it.path == file.path })
    }

    fun testDoubleClickOnAMissingResponseDoesNotOpenAnEditor() = withFreshResponse { _, list, file ->
        assertTrue(file.delete())

        click(list, 2)

        assertFalse(FileEditorManager.getInstance(project).openFiles.any { it.path == file.path })
    }

    fun testDoubleClickOnACachedResponseDeletedOnDiskDoesNotOpenAnEditor() = withFreshResponse { _, list, file ->
        assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file))
        assertTrue(file.delete())

        click(list, 2)

        assertFalse(FileEditorManager.getInstance(project).openFiles.any { it.path == file.path })
    }

    private fun withFreshResponse(action: (HistoryTabPanel, JList<*>, File) -> Unit) {
        val previous = HistoryPersistence.load()
        val directory = Files.createTempDirectory("kttp-history-action").toFile()
        val file = directory.resolve("fresh response.json")
        try {
            val files = LocalFileSystem.getInstance()
            requireNotNull(files.refreshAndFindFileByIoFile(directory)).children
            assertNull(files.findFileByPath(file.path))
            file.writeText("{\"fresh\":true}")
            assertNull("The new disk file must not already be in VFS", files.findFileByPath(file.path))
            val panel = HistoryTabPanel(project)
            panel.addEntry("fresh response", file.absolutePath, null)
            val list = HistoryTabPanel::class.java.getDeclaredField("list").apply { isAccessible = true }
                .get(panel) as JList<*>
            list.selectedIndex = list.model.size - 1

            action(panel, list, file)
        } finally {
            FileEditorManager.getInstance(project).openFiles.filter { it.path == file.path }
                .forEach { FileEditorManager.getInstance(project).closeFile(it) }
            HistoryPersistence.clear()
            HistoryPersistence.append(previous)
            directory.deleteRecursively()
        }
    }

    private fun waitForOpenFile(file: File) {
        PlatformTestUtil.waitWithEventsDispatching(
            "response file opens in editor",
            { FileEditorManager.getInstance(project).openFiles.any { it.path == file.path } },
            10
        )
    }

    private fun click(list: JList<*>, count: Int) {
        val event = MouseEvent(
            list,
            MouseEvent.MOUSE_CLICKED,
            System.currentTimeMillis(),
            0,
            1,
            1,
            count,
            false,
            MouseEvent.BUTTON1
        )
        list.mouseListeners.forEach { it.mouseClicked(event) }
    }
}
