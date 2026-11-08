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

package io.github.photowey.kttp.core.interceptor

import io.github.photowey.kttp.core.extension.jdk.uniqueFilePath
import okhttp3.MediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.ForwardingSource
import okio.buffer
import okio.source
import java.io.Closeable
import java.io.OutputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE

/**
 * Owns a newly created file until a complete response has been written.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal class ResponseStorage private constructor(
    val path: Path,
    val output: OutputStream
) : Closeable {
    private var complete = false

    fun complete() {
        output.close()
        complete = true
    }

    override fun close() {
        try {
            output.close()
        } finally {
            if (!complete) Files.deleteIfExists(path)
        }
    }

    /**
     * Creates a unique response output file within the configured storage directory.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    companion object {
        fun create(directory: Path, filename: String): ResponseStorage {
            Files.createDirectories(directory)
            val root = directory.toRealPath()
            while (true) {
                val path = uniqueFilePath(root, filename)
                try {
                    val output = Files.newOutputStream(path, CREATE_NEW, WRITE, NOFOLLOW_LINKS)
                    return ResponseStorage(path, output)
                } catch (_: FileAlreadyExistsException) {
                    // Another download reserved the basename; choose another without overwriting it.
                }
            }
        }
    }
}

internal fun Path.replayBody(contentType: MediaType?, deleteOnClose: Boolean = false): ResponseBody {
    val size = Files.size(this)
    val source = object : ForwardingSource(source(NOFOLLOW_LINKS)) {
        override fun close() {
            try {
                super.close()
            } finally {
                if (deleteOnClose) Files.deleteIfExists(this@replayBody)
            }
        }
    }.buffer()
    return source.asResponseBody(contentType, size)
}
