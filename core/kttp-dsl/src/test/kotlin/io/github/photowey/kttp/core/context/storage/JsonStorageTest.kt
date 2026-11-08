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

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_16BE
import java.nio.charset.StandardCharsets.UTF_16LE
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.reflect.typeOf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Verifies JSON persistence, session merging, archive encodings, and corruption preservation.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class JsonStorageTest {

    @TempDir
    lateinit var directory: Path

    private val file = createTempFile("kttp-json-storage", ".json")

    @AfterTest
    fun cleanup() {
        file.toFile().delete()
    }

    @Test
    fun `getValue returns null for missing key`() {
        val storage = JsonStorage(file)
        assertNull(storage.getValue<String>("missing", typeOf<String>()))
        storage.close()
    }

    @Test
    fun `setValue and getValue round-trip string`() {
        val storage = JsonStorage(file)
        storage.setValue("key", "value")
        assertEquals("value", storage.getValue("key", typeOf<String>()))
        storage.close()
    }

    @Test
    fun `setValue and getValue round-trip int`() {
        val storage = JsonStorage(file)
        storage.setValue("num", 42)
        assertEquals(42, storage.getValue("num", typeOf<Int>()))
        storage.close()
    }

    @Test
    fun `close persists changedData to file`() {
        val storage = JsonStorage(file)
        storage.setValue("persisted", "data")
        storage.close()

        val storage2 = JsonStorage(file)
        assertEquals("data", storage2.getValue("persisted", typeOf<String>()))
        storage2.close()
    }

    @Test
    fun `corrupt existing storage fails explicitly and preserves original bytes`() {
        val corrupt = "{\"kept\":\"saved\",BROKEN}"
        Files.writeString(file, corrupt)

        assertFailsWith<IOException> { JsonStorage(file) }

        assertEquals(corrupt, Files.readString(file))
    }

    @Test
    fun `invalid storage shape or trailing content cannot be silently accepted`() {
        for (invalid in listOf("null", "[]", "{\"kept\":\"saved\"} trailing")) {
            Files.writeString(file, invalid)

            assertFailsWith<IOException>(invalid) { JsonStorage(file) }

            assertEquals(invalid, Files.readString(file))
        }
    }

    @Test
    fun `corruption introduced during a session cannot be overwritten on close`() {
        Files.writeString(file, "{\"kept\":\"saved\"}")
        val storage = JsonStorage(file)
        storage.setValue("new", "value")
        val corrupt = "{\"kept\":\"saved\",BROKEN}"
        Files.writeString(file, corrupt)

        assertFailsWith<IOException> { storage.close() }

        assertEquals(corrupt, Files.readString(file))
    }

    @Test
    fun `failed serialization preserves previous storage and removes temporary files`() {
        val target = directory.resolve("variables.json")
        val original = "{\"kept\":\"saved\"}"
        Files.writeString(target, original)
        val mapper = ObjectMapper()
        val storage = JsonStorage(target, mapper)
        storage.setValue("new", "value")
        mapper.registerModule(SimpleModule().addSerializer(object : JsonSerializer<Map<*, *>>() {
            override fun handledType(): Class<Map<*, *>> = Map::class.java

            override fun serialize(value: Map<*, *>, gen: JsonGenerator, serializers: SerializerProvider) {
                gen.writeStartObject()
                gen.writeStringField("partial", "written")
                throw IOException("Archive serialization failed")
            }
        }))

        assertFailsWith<IOException> { storage.close() }

        assertEquals(original, Files.readString(target))
        Files.list(directory).use { assertEquals(listOf(target), it.toList()) }
    }

    @Test
    fun `close merges changes with other successful sessions`() {
        val first = JsonStorage(file)
        val second = JsonStorage(file)
        first.setValue("first", 1)
        second.setValue("second", 2)
        first.close()
        second.close()

        JsonStorage(file).use { reopened ->
            assertEquals(1, reopened.getValue("first", typeOf<Int>()))
            assertEquals(2, reopened.getValue("second", typeOf<Int>()))
        }
    }

    @Test
    fun `successful close does not replay old changes on a later close`() {
        val first = JsonStorage(file)
        first.setValue("token", "old")
        first.close()
        JsonStorage(file).use { it.setValue("token", "new") }

        first.close()

        JsonStorage(file).use { assertEquals("new", it.getValue("token", typeOf<String>())) }
    }

    @Test
    fun `UTF8 BOM archive remains readable and writable`() {
        assertEncodedArchive(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()), UTF_8)
    }

    @Test
    fun `UTF16 little endian BOM archive remains readable and writable`() {
        assertEncodedArchive(byteArrayOf(0xff.toByte(), 0xfe.toByte()), UTF_16LE)
    }

    @Test
    fun `UTF16 big endian BOM archive remains readable and writable`() {
        assertEncodedArchive(byteArrayOf(0xfe.toByte(), 0xff.toByte()), UTF_16BE)
    }

    @Test
    fun `empty or whitespace archive is an empty writable store`() {
        for (content in listOf("", " \n\t\r")) {
            Files.write(file, content.toByteArray(UTF_8))
            JsonStorage(file).use { storage ->
                assertNull(storage.getValue<String>("missing", typeOf<String>()))
                storage.setValue("new", "value")
            }
            JsonStorage(file).use { assertEquals("value", it.getValue("new", typeOf<String>())) }
        }
    }

    private fun assertEncodedArchive(bom: ByteArray, charset: Charset) {
        Files.write(file, bom + "{\"kept\":\"已保存\"}".toByteArray(charset))
        JsonStorage(file).use { storage ->
            assertEquals("已保存", storage.getValue("kept", typeOf<String>()))
            storage.setValue("new", "value")
        }
        JsonStorage(file).use { storage ->
            assertEquals("已保存", storage.getValue("kept", typeOf<String>()))
            assertEquals("value", storage.getValue("new", typeOf<String>()))
        }
    }
}
