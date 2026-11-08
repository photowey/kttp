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
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import io.github.photowey.kttp.idea.path.KttpConsolesPath
import io.github.photowey.kttp.idea.path.KttpProjectPath
import java.io.File
import java.util.concurrent.Callable

/**
 * Verifies response discovery sees files saved or deleted outside the IDE's VFS.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/08
 */
class SidebarFileDiscoveryTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()

    fun testDiscoversProjectResponseSavedAfterDirectoryWasCached() {
        verifyNewResponse(KttpProjectPath.getResponsesDir(requireNotNull(project.basePath)), false)
    }

    fun testDiscoversConsoleResponseSavedAfterDirectoryWasCached() {
        verifyNewResponse(KttpConsolesPath.getResponsesDir(), true)
    }

    fun testOmitsCachedResponseDeletedOnDisk() {
        val directory = KttpProjectPath.getResponsesDir(requireNotNull(project.basePath))
        val file = directory.resolve("deleted-response.json").apply { writeText("{}") }
        assertTrue(scan().project.any { it.path == file.path })
        assertTrue(file.delete())

        assertFalse(scan().project.any { it.path == file.path })
    }

    private fun verifyNewResponse(directory: File, console: Boolean) {
        val files = LocalFileSystem.getInstance()
        requireNotNull(files.refreshAndFindFileByIoFile(directory)).children
        val file = directory.resolve("sidebar-discovery-${System.nanoTime()}.json")
        try {
            file.writeText("{}")
            assertNull("The disk write must not already be known to VFS", files.findFileByPath(file.path))

            val groups = scan()

            assertTrue(
                "The saved response must appear without opening it first",
                (if (console) groups.kttp else groups.project).any { it.path == file.path }
            )
        } finally {
            file.delete()
        }
    }

    private fun scan(): SidebarFileGroups {
        val application = ApplicationManager.getApplication()
        return requireNotNull(application.threadingSupport)
            .releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
                PlatformTestUtil.waitForFuture(application.executeOnPooledThread(Callable {
                    SidebarFileDiscovery(project).responses()
                }))
            }
    }
}
