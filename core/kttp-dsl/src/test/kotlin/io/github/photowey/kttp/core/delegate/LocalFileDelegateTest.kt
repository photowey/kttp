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

package io.github.photowey.kttp.core.delegate

import io.github.photowey.kttp.core.context.script.ScriptContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.reflect.KProperty0

/**
 * Verifies local-file delegates across path formats, encodings, and missing-file behavior.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/18
 */
class LocalFileDelegateTest {

    /**
     * Supplies property metadata for local-file delegate tests.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/18
     */
    private class TestProperty {
        var content: String = ""
    }

    private fun createProperty(name: String): KProperty0<String> {
        val obj = TestProperty()
        return obj::content
    }

    @Test
    fun `localFile reads existing file with relative path`() {
        val scriptDir = File.createTempFile("script", ".kts").parent
        val testFile = File(scriptDir, "test.json")

        try {
            ScriptContext.setDirectory(scriptDir)
            testFile.writeText("""{"key": "value"}""", StandardCharsets.UTF_8)

            val delegate = localFile("test.json")
            val property = createProperty("content")
            val content = delegate.getValue(null, property)

            assertEquals("""{"key": "value"}""", content)
        } finally {
            testFile.delete()
            ScriptContext.clear()
        }
    }

    @Test
    fun `localFile reads existing file with absolute path`() {
        val testFile = File.createTempFile("test", ".json")

        try {
            testFile.writeText("""{"data": "test"}""", StandardCharsets.UTF_8)

            val delegate = localFile(testFile.absolutePath)
            val property = createProperty("content")
            val content = delegate.getValue(null, property)

            assertEquals("""{"data": "test"}""", content)
        } finally {
            testFile.delete()
        }
    }

    @Test
    fun `localFile reads file with file URI prefix`() {
        val testFile = File.createTempFile("test", ".txt")

        try {
            testFile.writeText("File content", StandardCharsets.UTF_8)

            val delegate = localFile("file://${testFile.absolutePath}")
            val property = createProperty("content")
            val content = delegate.getValue(null, property)

            assertEquals("File content", content)
        } finally {
            testFile.delete()
        }
    }

    @Test
    fun `localFile throws exception when file not found and required=true`() {
        val scriptDir = File.createTempFile("script", ".kts").parent

        try {
            ScriptContext.setDirectory(scriptDir)
            val delegate = localFile("nonexistent.json", required = true)
            val property = createProperty("content")

            assertFailsWith<IllegalStateException> {
                delegate.getValue(null, property)
            }
        } finally {
            ScriptContext.clear()
        }
    }

    @Test
    fun `localFile returns empty string when file not found and required=false`() {
        val scriptDir = File.createTempFile("script", ".kts").parent

        try {
            ScriptContext.setDirectory(scriptDir)
            val delegate = localFile("nonexistent.json", required = false)
            val property = createProperty("content")
            val content = delegate.getValue(null, property)

            assertEquals("", content)
        } finally {
            ScriptContext.clear()
        }
    }

    @Test
    fun `localFile reads file with custom charset`() {
        val testFile = File.createTempFile("test", ".txt")

        try {
            // Write with ISO-8859-1 encoding
            val content = "Café"
            val isoCharset = java.nio.charset.Charset.forName("ISO-8859-1")
            testFile.writeBytes(content.toByteArray(isoCharset))

            val delegate = localFile(testFile.absolutePath, charset = isoCharset)
            val property = createProperty("content")
            val readContent = delegate.getValue(null, property)

            assertEquals(content, readContent)
        } finally {
            testFile.delete()
        }
    }

    @Test
    fun `localFile absolutePath returns resolved path`() {
        val testFile = File.createTempFile("test", ".txt")

        try {
            val delegate = localFile(testFile.absolutePath)
            val absolutePath = delegate.absolutePath()

            assertTrue(absolutePath.contains("test"))
            assertTrue(absolutePath.contains(".txt"))
        } finally {
            testFile.delete()
        }
    }

    @Test
    fun `localFile exists returns true for existing file`() {
        val testFile = File.createTempFile("test", ".txt")

        try {
            val delegate = localFile(testFile.absolutePath)
            assertTrue(delegate.exists())
        } finally {
            testFile.delete()
        }
    }

    @Test
    fun `localFile exists returns false for non-existent file`() {
        val delegate = localFile("/nonexistent/file.txt")
        assertFalse(delegate.exists())
    }

    @Test
    fun `localFile readBytes returns file content as bytes`() {
        val testFile = File.createTempFile("test", ".bin")

        try {
            val content = byteArrayOf(0x01, 0x02, 0x03, 0x04)
            testFile.writeBytes(content)

            val delegate = localFile(testFile.absolutePath)
            val bytes = delegate.readBytes()

            assertTrue(bytes.contentEquals(content))
        } finally {
            testFile.delete()
        }
    }

    @Test
    fun `localFile readBytes throws exception when file not found and required=true`() {
        val delegate = localFile("/nonexistent/file.bin", required = true)

        assertFailsWith<IllegalStateException> {
            delegate.readBytes()
        }
    }

    @Test
    fun `localFile resolves relative path using ScriptContext`() {
        val scriptDir = File.createTempFile("script", ".kts").parent
        val subDir = File(scriptDir, "data").apply { mkdirs() }
        val testFile = File(subDir, "request.json")

        try {
            ScriptContext.setDirectory(scriptDir)
            testFile.writeText("""{"request": "data"}""", StandardCharsets.UTF_8)

            val delegate = localFile("data/request.json")
            val property = createProperty("content")
            val content = delegate.getValue(null, property)

            assertEquals("""{"request": "data"}""", content)
        } finally {
            testFile.delete()
            subDir.delete()
            ScriptContext.clear()
        }
    }

    @Test
    fun `localFile handles nested relative paths`() {
        val scriptDir = File.createTempFile("script", ".kts").parent
        val nestedDir = File(scriptDir, "api/v1").apply { mkdirs() }
        val testFile = File(nestedDir, "config.json")

        try {
            ScriptContext.setDirectory(scriptDir)
            testFile.writeText("""{"version": "1.0"}""", StandardCharsets.UTF_8)

            val delegate = localFile("api/v1/config.json")
            val property = createProperty("content")
            val content = delegate.getValue(null, property)

            assertEquals("""{"version": "1.0"}""", content)
        } finally {
            testFile.delete()
            nestedDir.parentFile?.delete()
            nestedDir.parentFile?.parentFile?.delete()
            ScriptContext.clear()
        }
    }

    @Test
    fun `localFile function creates delegate correctly`() {
        val testFile = File.createTempFile("test", ".txt")

        try {
            testFile.writeText("Test", StandardCharsets.UTF_8)

            val delegate = localFile(testFile.absolutePath)
            assertTrue(delegate.exists())
        } finally {
            testFile.delete()
        }
    }

    @Test
    fun `createLocalFile function creates delegate correctly`() {
        val testFile = File.createTempFile("test", ".txt")

        try {
            testFile.writeText("Test", StandardCharsets.UTF_8)

            val delegate = createLocalFile(testFile.absolutePath)
            assertTrue(delegate.exists())
        } finally {
            testFile.delete()
        }
    }
}
