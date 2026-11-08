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

import io.github.photowey.kttp.core.annotation.KttpDSL
import io.github.photowey.kttp.core.context.script.ScriptContext
import io.github.photowey.kttp.core.resource.ResourceResolver
import kotlin.reflect.KProperty
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Local file content delegate.
 *
 * Supports Spring Resource-style path resolution:
 * - Absolute path (starts with `/`): file system path, preferred form. Equivalent to file:// (file:// is implicit).
 * - `file://` prefix: removes the prefix and treats the remainder as a filesystem path.
 *   For example, file:///absolute/path/data.json and /absolute/path/data.json resolve to the same file.
 * - Nonempty `classpath:/` resource path: when a base directory is available, reads from its
 *   `src/main/resources` directory; otherwise reads from the JVM classpath.
 * - Project path (contains `src/`): searches ancestors for a directory containing `src/`,
 *   starting at the base directory and then the working directory; falls back to the working directory.
 * - Relative path: resolved relative to the "base directory". When the runner passes
 *   a base dir (e.g. IDE passes project root via --base-dir), relative paths are
 *   resolved against that, like classpath; otherwise the script file directory is used when available,
 *   falling back to the working directory when no directory was supplied.
 *
 * Usage example:
 * ```kotlin
 * // Relative path (relative to base dir / script location)
 * val body: String by localFile("request.json")
 *
 * // Absolute path (recommended; no need for file:// prefix)
 * val config: String by localFile("/path/to/config.json")
 *
 * // File URI (optional; same as absolute path)
 * val data: String by localFile("file:///absolute/path/to/data.json")
 *
 * // Resource under baseDir/src/main/resources, or the JVM classpath when no base dir is available
 * val template: String by localFile("classpath:/templates/request.json")
 *
 * // Project path
 * val resource: String by localFile("src/main/resources/data.json")
 * ```
 *
 * @param path File path (supports multiple formats)
 * @param charset File encoding, defaults to UTF-8
 * @param required Whether the file must exist (default: true)
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/18
 */
@KttpDSL
class LocalFileDelegate(
    private val path: String,
    private val charset: Charset = StandardCharsets.UTF_8,
    private val required: Boolean = true
) {
    private val resolver = ResourceResolver(ScriptContext.scriptDirectory)

    /**
     * Resolves and reads text on each property access. Missing or unreadable optional resources return an empty string;
     * required resources raise IllegalStateException with the resolved resource identifier.
     */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): String {
        val resource = resolver.resolve(path)

        if (!resource.exists()) {
            if (required) {
                throw IllegalStateException("Local file not found: ${resource.getAbsolutePath()}")
            }
            return ""
        }

        return try {
            resource.readText(charset)
        } catch (e: Exception) {
            if (required) {
                throw IllegalStateException("Failed to read file: ${resource.getAbsolutePath()}", e)
            }
            ""
        }
    }

    /**
     * Gets the resolved absolute file path, or a `classpath:/...` identifier for a JVM classpath resource.
     */
    fun absolutePath(): String {
        return resolver.resolve(path).getAbsolutePath()
    }

    /**
     * Checks if the file exists.
     */
    fun exists(): Boolean {
        return resolver.resolve(path).exists()
    }

    /**
     * Reads the file as a byte array.
     */
    fun readBytes(): ByteArray {
        val resource = resolver.resolve(path)
        if (!resource.exists() && required) {
            throw IllegalStateException("Local file not found: ${resource.getAbsolutePath()}")
        }
        return resource.readBytes()
    }
}

/**
 * Creates a local file delegate.
 *
 * @param path File path
 * @param charset File encoding
 * @param required Whether the file must exist
 */
fun createLocalFile(
    path: String,
    charset: Charset = StandardCharsets.UTF_8,
    required: Boolean = true
): LocalFileDelegate {
    return LocalFileDelegate(path, charset, required)
}

/**
 * Top-level local file delegate.
 * Usage: val content: String by localFile("request.json")
 *
 * This function is available in Kttp scripts via default imports.
 */
@KttpDSL
fun localFile(
    path: String,
    charset: Charset = StandardCharsets.UTF_8,
    required: Boolean = true
): LocalFileDelegate {
    return LocalFileDelegate(path, charset, required)
}
