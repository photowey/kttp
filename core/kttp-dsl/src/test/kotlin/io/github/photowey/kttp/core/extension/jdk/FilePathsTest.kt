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

package io.github.photowey.kttp.core.extension.jdk

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Verifies safe response basenames and collision handling, including symbolic links.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class FilePathsTest {
    @TempDir
    lateinit var temporaryDir: Path

    @Test
    fun `untrusted names resolve to a basename inside the response directory`() {
        val responses = Files.createDirectory(temporaryDir.resolve("responses"))
        val cases = listOf(
            "../outside.txt" to "outside.txt",
            temporaryDir.resolve("absolute.txt").toString() to "absolute.txt",
            "..\\outside.txt" to "outside.txt",
            "C:\\downloads\\report.json" to "report.json",
            "nested/path/result.txt" to "result.txt"
        )

        for ((input, expectedName) in cases) {
            val path = uniqueFilePath(responses, input)
            assertEquals(responses, path.toAbsolutePath().normalize().parent, input)
            assertEquals(expectedName, path.fileName.toString(), input)
        }
    }

    @Test
    fun `empty and unusable names produce a regular filename`() {
        for (input in listOf("", " ", ".", "..", "/", "\\", "\u0000")) {
            val path = uniqueFilePath(temporaryDir, input)
            assertEquals(temporaryDir, path.toAbsolutePath().normalize().parent, input)
            assertTrue(path.fileName.toString().isNotBlank(), input)
            assertNotEquals(".", path.fileName.toString())
            assertNotEquals("..", path.fileName.toString())
        }
    }

    @Test
    fun `existing file and dangling symlink are both treated as occupied names`() {
        Files.writeString(temporaryDir.resolve("result.json"), "original")
        Files.createSymbolicLink(temporaryDir.resolve("result-1.json"), temporaryDir.resolve("outside.json"))

        assertEquals("result-2.json", uniqueFilePath(temporaryDir, "result.json").fileName.toString())
        assertEquals("original", Files.readString(temporaryDir.resolve("result.json")))
    }
}
