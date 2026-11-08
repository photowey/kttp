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

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

/**
 * Missing state is empty; unreadable or invalid state must remain available for recovery.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal object AtomicStateFile {
    /** Reads the archive as UTF-8; absence returns null, while an unreadable archive raises StatePersistenceException. */
    fun read(file: File): String? {
        val path = file.toPath()
        if (Files.notExists(path, NOFOLLOW_LINKS)) return null
        return try {
            Files.readString(path)
        } catch (failure: IOException) {
            throw StatePersistenceException("Cannot read state archive: ${file.absolutePath}", failure)
        }
    }

    /**
     * Forces a UTF-8 sibling temporary file before atomic replacement and removes temporary output on failure.
     * Unsupported atomic moves fail explicitly rather than overwriting the previous archive non-atomically.
     */
    fun replace(file: File, content: String) {
        val path = file.toPath().toAbsolutePath()
        val directory = path.parent
        var temporary: java.nio.file.Path? = null
        try {
            Files.createDirectories(directory)
            temporary = Files.createTempFile(directory, ".${file.name}.", ".tmp")
            FileChannel.open(temporary, WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(content.toByteArray(Charsets.UTF_8))
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            // If atomic replacement is unsupported, fail without touching the previous archive.
            Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (failure: IOException) {
            throw StatePersistenceException("Cannot save state archive: ${file.absolutePath}", failure)
        } finally {
            temporary?.let { Files.deleteIfExists(it) }
        }
    }
}

/**
 * Reports a state archive read or write failure without treating it as missing state.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class StatePersistenceException(message: String, cause: Throwable) : IOException(message, cause)
