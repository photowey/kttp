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

package io.github.photowey.kttp.idea.env

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

/**
 * Verifies environment discovery establishes PSI read access for callers outside an IDE read action.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/14
 */
class EnvironmentReadAccessTest : BasePlatformTestCase() {
    fun testEnvironmentProfilesCanBeReadWithoutCallerReadLock() {
        myFixture.addFileToProject("env/kttp.env.json", "{\"dev\":{\"host\":\"https://example.com\"}}")
        val script = myFixture.addFileToProject("env/test.kttp.kts", "// test").virtualFile
        val service = KttpEnvironmentService.getInstance(project)
        service.setSelectedEnvironment("dev")
        val result = ApplicationManager.getApplication().executeOnPooledThread(Callable {
            assertFalse(ApplicationManager.getApplication().isReadAccessAllowed)
            val profiles = service.getAvailableEnvironments(script)
            profiles.map { it.name } to service.getEnvironmentVariables(script)
        }).get(15, TimeUnit.SECONDS)
        assertEquals(listOf("dev"), result.first)
        assertEquals(mapOf("host" to "https://example.com"), result.second)
    }
}
