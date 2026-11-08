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

package io.github.photowey.kttp.core.context.store

import io.github.photowey.kttp.core.context.storage.InMemoryStorage
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Verifies named variable reads, writes, and replacement in script storage.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class VariablesStoreTest {

    @Test
    fun `setValue and getValue round-trip by name`() {
        val store = VariablesStore(InMemoryStorage())
        store.setValue("name", "kttp")
        assertEquals("kttp", store.getValue("name", typeOf<String>()))
    }

    @Test
    fun `getValue returns null for missing key`() {
        val store = VariablesStore(InMemoryStorage())
        assertNull(store.getValue<String>("missing", typeOf<String>()))
    }

    @Test
    fun `setValue overwrites previous value`() {
        val store = VariablesStore(InMemoryStorage())
        store.setValue("x", 1)
        store.setValue("x", 2)
        assertEquals(2, store.getValue("x", typeOf<Int>()))
    }
}
