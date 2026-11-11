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

package io.github.photowey.kttp.idea.path

import java.io.File
import java.net.JarURLConnection
import java.net.URL

/**
 * Resolves the installed plugin root from its own JAR without plugin-manager internals.
 *
 * @author photowey
 * @version 1.0.1
 * @since 2026/11/11
 */
internal object KttpPluginPath {
    /** Plugin class loaders may omit the code source or point it at a directory; the class resource identifies its JAR. */
    fun installedRoot(): File? {
        val type = KttpPluginPath::class.java
        return fromClassLocation(type.protectionDomain?.codeSource?.location)
            ?: fromClassLocation(type.getResource("/${type.name.replace('.', '/')}.class"))
    }

    /** URI decoding preserves spaces, non-ASCII characters and literal plus signs in installation paths. */
    internal fun fromClassLocation(location: URL?): File? {
        val file = try {
            when (location?.protocol) {
                "file" -> File(location.toURI())
                "jar" -> File((location.openConnection() as JarURLConnection).jarFileURL.toURI())
                else -> return null
            }
        } catch (_: Exception) {
            return null
        }
        val lib = file.takeIf { it.isFile }?.parentFile ?: return null
        return lib.takeIf { it.name == "lib" }?.parentFile
    }
}
