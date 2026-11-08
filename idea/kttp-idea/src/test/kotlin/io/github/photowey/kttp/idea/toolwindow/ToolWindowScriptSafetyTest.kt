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

import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.photowey.kttp.idea.KttpToolWindowFactory
import io.github.photowey.kttp.idea.path.KttpProjectPath
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Verifies opening the tool window preserves existing scripts regardless of age or naming patterns.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class ToolWindowScriptSafetyTest : BasePlatformTestCase() {
    fun testOpeningToolWindowPreservesSavedScriptsRegardlessOfAgeOrPrefixCount() {
        val directory = KttpProjectPath.getRequestsDir(requireNotNull(project.basePath))
        val scripts = (0..102).map { index ->
            File(directory, "gui-request-orders-$index.kttp.kts").apply {
                writeText("GET(\"https://example.com/orders/$index\") {}")
            }
        }
        assertTrue(scripts.first().setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8)))
        val manager = ToolWindowManager.getInstance(project)
        val window = manager.registerToolWindow(RegisterToolWindowTask.notClosable("KttpScriptSafety"))
        try {
            KttpToolWindowFactory().createToolWindowContent(project, window)
            PlatformTestUtil.waitForAllBackgroundActivityToCalmDown()
            scripts.forEachIndexed { index, file ->
                assertTrue("Saved script was removed: ${file.name}", file.isFile)
                assertEquals("GET(\"https://example.com/orders/$index\") {}", file.readText())
            }
        } finally {
            window.remove()
            scripts.forEach(File::delete)
        }
    }
}
