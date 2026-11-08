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

package io.github.photowey.kttp.core.context.script

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies script directory and environment setup, replacement, and cleanup.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/18
 */
class ScriptContextTest {

    @Test
    fun `setDirectory sets script directory`() {
        ScriptContext.setDirectory("/path/to/script")
        assertEquals("/path/to/script", ScriptContext.scriptDirectory)
        ScriptContext.clear()
    }

    @Test
    fun `setEnvironment sets environment variables`() {
        val env = mapOf("KEY1" to "value1", "KEY2" to "value2")
        ScriptContext.setEnvironment(env)
        assertEquals(env, ScriptContext.environment)
        ScriptContext.clear()
    }

    @Test
    fun `clear resets all context`() {
        ScriptContext.setDirectory("/path/to/script")
        ScriptContext.setEnvironment(mapOf("KEY" to "value"))

        ScriptContext.clear()

        assertNull(ScriptContext.scriptDirectory)
        assertTrue(ScriptContext.environment.isEmpty())
    }

    @Test
    fun `scriptDirectory is null initially`() {
        ScriptContext.clear() // Ensure clean state
        assertNull(ScriptContext.scriptDirectory)
    }

    @Test
    fun `environment is empty initially`() {
        ScriptContext.clear() // Ensure clean state
        assertTrue(ScriptContext.environment.isEmpty())
    }

    @Test
    fun `multiple setDirectory calls update directory`() {
        ScriptContext.setDirectory("/first/path")
        assertEquals("/first/path", ScriptContext.scriptDirectory)

        ScriptContext.setDirectory("/second/path")
        assertEquals("/second/path", ScriptContext.scriptDirectory)

        ScriptContext.clear()
    }

    @Test
    fun `multiple setEnvironment calls update environment`() {
        ScriptContext.setEnvironment(mapOf("KEY1" to "value1"))
        assertEquals("value1", ScriptContext.environment["KEY1"])

        ScriptContext.setEnvironment(mapOf("KEY2" to "value2"))
        assertEquals("value2", ScriptContext.environment["KEY2"])
        assertNull(ScriptContext.environment["KEY1"])

        ScriptContext.clear()
    }
}
