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

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies typed environment lookup, store precedence, and missing-variable diagnostics.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class EnvironmentStoreTest {

    @TempDir
    lateinit var environmentDirectory: Path

    /**
     * Supplies property metadata for the supported environment value types.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    private class EnvProps {
        val str: String get() = ""
        val num: Int get() = 0
        val flag: Boolean get() = false
        val double: Double get() = 0.0
        val long: Long get() = 0L
    }

    @Test
    fun `FileEnvironmentStore reads only the selected profile`() {
        val store = fileStore("""{"dev":{"str":"selected"},"test":{"num":42}}""")

        assertEquals("selected", store.getValue<String>(null, EnvProps::str))
        assertFalse(store.contains(EnvProps::num))
        assertFailsWith<NoEnvironmentPropertyException> {
            store.getValue<Int>(null, EnvProps::num)
        }
    }

    @Test
    fun `FileEnvironmentStore reports a missing profile during value lookup`() {
        val store = fileStore("""{"test":{"str":"other"}}""")

        val error = assertFailsWith<IllegalArgumentException> {
            store.getValue<String>(null, EnvProps::str)
        }
        assertTrue(error.message.orEmpty().contains("dev"))
        assertTrue(error.message.orEmpty().contains("kttp.env.json"))
    }

    @Test
    fun `FileEnvironmentStore reports a missing profile during membership lookup`() {
        val store = fileStore("{}")

        assertFailsWith<IllegalArgumentException> {
            store.contains(EnvProps::str)
        }
    }

    @Test
    fun `FileEnvironmentStore rejects profiles that are not JSON objects`() {
        for (value in listOf("null", "[]", "42", "true", "\"text\"")) {
            val store = fileStore("""{"dev":$value}""")

            assertFailsWith<IllegalArgumentException>("Profile value: $value") {
                store.contains(EnvProps::str)
            }
        }
    }

    @Test
    fun `FileEnvironmentStore rejects roots that are not JSON objects`() {
        for (content in listOf("", "null", "[]", "42", "\"text\"")) {
            val store = fileStore(content)

            assertFailsWith<IllegalArgumentException>("Root value: $content") {
                store.contains(EnvProps::str)
            }
        }
    }

    private fun fileStore(content: String): FileEnvironmentStore {
        val file = environmentDirectory.resolve("kttp.env.json").toFile()
        file.writeText(content)
        return FileEnvironmentStore(file, "dev")
    }

    @Test
    fun `ValuesEnvironmentStore getValue returns String`() {
        val store = ValuesEnvironmentStore(mapOf("str" to "hello"))
        assertEquals("hello", store.getValue<String>(null, EnvProps::str))
    }

    @Test
    fun `ValuesEnvironmentStore getValue returns Int`() {
        val store = ValuesEnvironmentStore(mapOf("num" to "42"))
        assertEquals(42, store.getValue<Int>(null, EnvProps::num))
    }

    @Test
    fun `ValuesEnvironmentStore getValue returns Boolean`() {
        val store = ValuesEnvironmentStore(mapOf("flag" to "true"))
        assertTrue(store.getValue<Boolean>(null, EnvProps::flag))
    }

    @Test
    fun `ValuesEnvironmentStore getValue returns Double and Long`() {
        val store = ValuesEnvironmentStore(mapOf("double" to "1.5", "long" to "100"))
        assertEquals(1.5, store.getValue<Double>(null, EnvProps::double))
        assertEquals(100L, store.getValue<Long>(null, EnvProps::long))
    }

    @Test
    fun `ValuesEnvironmentStore contains returns true when key present`() {
        val store = ValuesEnvironmentStore(mapOf("str" to "v"))
        assertTrue(store.contains(EnvProps::str))
    }

    @Test
    fun `ValuesEnvironmentStore contains returns false when key absent`() {
        val store = ValuesEnvironmentStore(mapOf("other" to "v"))
        assertFalse(store.contains(EnvProps::str))
    }

    @Test
    fun `ValuesEnvironmentStore getValue throws NoEnvironmentPropertyException when missing`() {
        val store = ValuesEnvironmentStore(emptyMap())
        assertFailsWith<NoEnvironmentPropertyException> {
            store.getValue<String>(null, EnvProps::str)
        }
    }

    @Test
    fun `CompoundEnvironmentStore returns value from first store that contains`() {
        val first = ValuesEnvironmentStore(mapOf("str" to "first"))
        val second = ValuesEnvironmentStore(mapOf("str" to "second"))
        val compound = CompoundEnvironmentStore(listOf(first, second))
        assertEquals("first", compound.getValue<String>(null, EnvProps::str))
    }

    @Test
    fun `CompoundEnvironmentStore falls back to second store`() {
        val first = ValuesEnvironmentStore(emptyMap())
        val second = ValuesEnvironmentStore(mapOf("str" to "second"))
        val compound = CompoundEnvironmentStore(listOf(first, second))
        assertEquals("second", compound.getValue<String>(null, EnvProps::str))
    }

    @Test
    fun `CompoundEnvironmentStore throws when no store contains property`() {
        val compound = CompoundEnvironmentStore(
            listOf(
                ValuesEnvironmentStore(emptyMap()),
                ValuesEnvironmentStore(emptyMap())
            )
        )
        assertFailsWith<NoEnvironmentPropertyException> {
            compound.getValue<String>(null, EnvProps::str)
        }
    }

    @Test
    fun `NoopEnvironmentStore contains always false`() {
        assertFalse(NoopEnvironmentStore.contains(EnvProps::str))
    }

    @Test
    fun `NoopEnvironmentStore getValue always throws`() {
        assertFailsWith<NoEnvironmentPropertyException> {
            NoopEnvironmentStore.getValue<String>(null, EnvProps::str)
        }
    }

    @Test
    fun `LenientEnvironmentStore getValue records unset key and takeUnsetKeysAndClear returns and clears`() {
        val delegate = ValuesEnvironmentStore(emptyMap())
        val lenient = LenientEnvironmentStore(delegate)
        assertEquals("", lenient.getValue<String>(null, EnvProps::str))
        assertEquals(setOf("str"), lenient.takeUnsetKeysAndClear())
        assertEquals(emptySet(), lenient.takeUnsetKeysAndClear())
    }

    @Test
    fun `LenientEnvironmentStore when delegate has value does not record key`() {
        val delegate = ValuesEnvironmentStore(mapOf("str" to "fromDelegate"))
        val lenient = LenientEnvironmentStore(delegate)
        assertEquals("fromDelegate", lenient.getValue<String>(null, EnvProps::str))
        assertEquals(emptySet(), lenient.takeUnsetKeysAndClear())
    }

    @Test
    fun `LenientEnvironmentStore multiple unset reads all captured in one takeUnsetKeysAndClear`() {
        val delegate = ValuesEnvironmentStore(emptyMap())
        val lenient = LenientEnvironmentStore(delegate)
        lenient.getValue<String>(null, EnvProps::str)
        lenient.getValue<Int>(null, EnvProps::num)
        assertEquals(setOf("str", "num"), lenient.takeUnsetKeysAndClear())
        assertEquals(emptySet(), lenient.takeUnsetKeysAndClear())
    }

    @Test
    fun `StrictEnvironmentStore empty map getValue throws NoEnvironmentSelectedException`() {
        val store = StrictEnvironmentStore(emptyMap())
        assertFailsWith<NoEnvironmentSelectedException> {
            store.getValue<String>(null, EnvProps::str)
        }
    }

    @Test
    fun `StrictEnvironmentStore key missing in non-empty map throws EnvironmentVariableNotFoundException`() {
        val store = StrictEnvironmentStore(mapOf("other" to "v"))
        assertFailsWith<EnvironmentVariableNotFoundException> {
            store.getValue<String>(null, EnvProps::str)
        }
    }

    @Test
    fun `StrictEnvironmentStore key present returns value`() {
        val store = StrictEnvironmentStore(mapOf("str" to "ok"))
        assertEquals("ok", store.getValue<String>(null, EnvProps::str))
    }
}
