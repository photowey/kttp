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
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import io.github.photowey.kttp.idea.viewer.ResponseViewer
import java.awt.Component
import java.awt.Container
import java.awt.datatransfer.DataFlavor
import java.nio.file.Files
import javax.swing.JButton

/**
 * Verifies response opening and path copying for missing, newly created, and cached files.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class ResponseActionsTest : BasePlatformTestCase() {
    fun testCachedResponseDeletedOnDiskDoesNotOpenAnEditor() {
        val file = Files.createTempDirectory("kttp-deleted-response").resolve("response.json").toFile()
        file.writeText("{}")
        assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file))
        val viewer = ResponseViewer(project)
        viewer.setResponse(200, emptyMap(), "{}", true, emptyList(), file.path)
        assertTrue(file.delete())
        try {
            descendants(viewer).filterIsInstance<JButton>().single { it.accessibleContext.accessibleName == "Open in Editor" }.doClick()

            assertFalse(FileEditorManager.getInstance(project).openFiles.any { it.path == file.path })
        } finally {
            FileEditorManager.getInstance(project).openFiles.filter { it.path == file.path }
                .forEach { FileEditorManager.getInstance(project).closeFile(it) }
            file.parentFile.delete()
        }
    }

    fun testNewResponseOpensWithoutImplicitWriteIntentFromAnAccessibilityCallback() {
        val file = Files.createTempDirectory("kttp-accessible-response").resolve("response.json").toFile()
        file.writeText("{\"accessible\":true}")
        val viewer = ResponseViewer(project)
        viewer.setResponse(200, emptyMap(), "{}", true, emptyList(), file.path)
        try {
            val application = ApplicationManager.getApplication()
            requireNotNull(application.threadingSupport).releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
                assertTrue(application.isDispatchThread)
                assertFalse(application.isWriteIntentLockAcquired)
                descendants(viewer).filterIsInstance<JButton>().single { it.accessibleContext.accessibleName == "Open in Editor" }.doClick()
            }

            PlatformTestUtil.waitWithEventsDispatching("Response opens asynchronously", {
                FileEditorManager.getInstance(project).openFiles.any { it.path == file.path }
            }, 5)
        } finally {
            FileEditorManager.getInstance(project).openFiles.filter { it.path == file.path }
                .forEach { FileEditorManager.getInstance(project).closeFile(it) }
            file.delete()
            file.parentFile.delete()
        }
    }

    fun testNewResponseCanBeOpenedAndItsPathCopiedImmediately() {
        val file = Files.createTempDirectory("kttp-response-action").resolve("response.json").toFile()
        file.writeText("{\"ok\":true}")
        val viewer = ResponseViewer(project)
        viewer.setResponse(200, emptyMap(), "{}", true, emptyList(), file.path)
        try {
            descendants(viewer).filterIsInstance<JButton>().single { it.accessibleContext.accessibleName == "Open in Editor" }.doClick()
            PlatformTestUtil.waitWithEventsDispatching("Response opens asynchronously", {
                FileEditorManager.getInstance(project).openFiles.any { it.path == file.path }
            }, 5)
            descendants(viewer).filterIsInstance<JButton>().single { it.accessibleContext.accessibleName == "Copy Path" }.doClick()
            assertEquals(file.path, CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor))
        } finally {
            FileEditorManager.getInstance(project).openFiles.filter { it.path == file.path }
                .forEach { FileEditorManager.getInstance(project).closeFile(it) }
            file.delete()
            file.parentFile.delete()
        }
    }

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
    }
}
