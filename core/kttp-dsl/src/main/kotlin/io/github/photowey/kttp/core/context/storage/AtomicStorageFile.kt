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

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

/**
 * Missing files are empty; unreadable files must remain available for recovery.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal object AtomicStorageFile {
    /** Returns null only when the archive is absent; unreadable files raise IOException instead of becoming empty state. */
    fun read(file: Path): ByteArray? {
        if (Files.notExists(file, NOFOLLOW_LINKS)) return null
        return try {
            Files.readAllBytes(file)
        } catch (failure: IOException) {
            throw IOException("Cannot read storage: ${file.toAbsolutePath()}", failure)
        }
    }

    /**
     * Writes and forces a sibling temporary file, then atomically replaces the archive.
     * Unsupported atomic replacement fails without falling back to a non-atomic overwrite; temporary files are removed.
     */
    fun replace(file: Path, write: (Path) -> Unit) {
        val target = file.toAbsolutePath()
        var temporary: Path? = null
        try {
            Files.createDirectories(target.parent)
            temporary = Files.createTempFile(target.parent, ".${target.fileName}.", ".tmp")
            write(temporary)
            FileChannel.open(temporary, WRITE).use { it.force(true) }
            // Unsupported atomic replacement must fail without touching the previous file.
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (failure: IOException) {
            throw IOException("Cannot save storage: $target", failure)
        } finally {
            temporary?.let { Files.deleteIfExists(it) }
        }
    }
}
