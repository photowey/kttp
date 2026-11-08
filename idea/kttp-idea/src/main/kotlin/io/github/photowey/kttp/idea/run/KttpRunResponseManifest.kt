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

package io.github.photowey.kttp.idea.run

import kotlinx.serialization.json.Json
import java.io.File

/**
 * Reads the CLI's process-specific response manifest; console output is never used as metadata.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal object KttpRunResponseManifest {
    fun read(manifest: File, responseDirectory: File): List<File> {
        if (!manifest.isFile) return emptyList()
        val directory = responseDirectory.canonicalFile.toPath()
        val files = linkedMapOf<String, File>()
        manifest.useLines { lines ->
            lines.forEach { line ->
                val path = runCatching { Json.decodeFromString<String>(line) }.getOrNull()
                val file = path?.let { runCatching { File(it).canonicalFile }.getOrNull() }
                if (file != null && file.isFile && file.name != "kttp-history.json" && file.toPath()
                        .startsWith(directory)
                ) {
                    files.remove(file.path)
                    files[file.path] = file
                    if (files.size > 100) files.remove(files.keys.first())
                }
            }
        }
        return files.values.toList()
    }
}
