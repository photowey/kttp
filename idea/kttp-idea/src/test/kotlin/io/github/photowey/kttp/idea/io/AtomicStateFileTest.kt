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

package io.github.photowey.kttp.idea.io

import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies atomic state replacement and preservation of existing files after read or write failures.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class AtomicStateFileTest {
    @Test
    fun successfulReplacementPreservesUnicodeAndLeavesNoTemporaryFiles() = withDirectory { directory ->
        val file = directory.resolve("state.json")
        assertNull(AtomicStateFile.read(file))
        AtomicStateFile.replace(file, "旧存档")
        AtomicStateFile.replace(file, "new state\n")
        assertEquals("new state\n", AtomicStateFile.read(file))
        assertEquals(listOf("state.json"), directory.list()!!.toList())
    }

    @Test
    fun failedReplacementLeavesTheExistingTargetAndRemovesTheTemporaryFile() = withDirectory { directory ->
        val target = directory.resolve("state.json").apply { mkdir() }
        val source = target.resolve("recoverable-source.json").apply { writeText("original archive") }
        assertFailsWith<StatePersistenceException> { AtomicStateFile.replace(target, "replacement") }
        assertEquals("original archive", source.readText())
        assertEquals(listOf("state.json"), directory.list()!!.toList())
    }

    @Test
    fun directoryAndDanglingLinkAreReadFailuresRatherThanMissingArchives() = withDirectory { directory ->
        assertFailsWith<StatePersistenceException> { AtomicStateFile.read(directory) }
        val link = directory.resolve("dangling.json").toPath()
        Files.createSymbolicLink(link, directory.resolve("missing.json").toPath())
        assertFailsWith<StatePersistenceException> { AtomicStateFile.read(link.toFile()) }
        assertTrue(Files.isSymbolicLink(link))
    }

    private fun withDirectory(action: (java.io.File) -> Unit) {
        val directory = Files.createTempDirectory("kttp-atomic-state").toFile()
        try {
            action(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
