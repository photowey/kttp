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

package io.github.photowey.kttp.core.context.storage

import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Verifies typed value round trips and missing-value behavior in memory storage.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class InMemoryStorageTest {

    @Test
    fun `getValue returns null for missing key`() {
        val storage = InMemoryStorage()
        assertNull(storage.getValue<String>("missing", typeOf<String>()))
    }

    @Test
    fun `setValue and getValue round-trip string`() {
        val storage = InMemoryStorage()
        storage.setValue("key", "value")
        assertEquals("value", storage.getValue("key", typeOf<String>()))
    }

    @Test
    fun `setValue and getValue round-trip int`() {
        val storage = InMemoryStorage()
        storage.setValue("num", 42)
        assertEquals(42, storage.getValue("num", typeOf<Int>()))
    }

    @Test
    fun `setValue null and getValue returns null`() {
        val storage = InMemoryStorage()
        storage.setValue("key", "first")
        storage.setValue("key", null)
        assertNull(storage.getValue<String>("key", typeOf<String>()))
    }

    @Test
    fun `close is no-op`() {
        val storage = InMemoryStorage()
        storage.setValue("k", "v")
        storage.close()
        assertEquals("v", storage.getValue("k", typeOf<String>()))
    }
}
