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

import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS

private const val DEFAULT_RESPONSE_FILENAME = "response"

/** Selects a safe unused basename; callers creating files must still use CREATE_NEW. */
fun uniqueFilePath(parentDir: Path, originalFileName: String): Path {
    val root = parentDir.toAbsolutePath().normalize()
    val original = safeFileName(originalFileName)
    var counter = 0
    var candidate = root.resolve(original)
    while (Files.exists(candidate, NOFOLLOW_LINKS)) {
        counter++
        candidate = root.resolve(withSuffix(original, counter))
    }
    check(candidate.parent == root) { "Response filename escapes its storage directory" }
    return candidate
}

private fun safeFileName(untrustedName: String): String {
    val basename = untrustedName.replace('\\', '/').substringAfterLast('/')
    val sanitized = basename.map { character ->
        if (character.isISOControl() || character in "<>:\"|?*") '_' else character
    }.joinToString("").trim().trimEnd('.')
    return sanitized.takeUnless { it.isBlank() || it.all { character -> character == '_' } }
        ?: DEFAULT_RESPONSE_FILENAME
}

private fun withSuffix(filename: String, counter: Int): String {
    val dot = filename.lastIndexOf('.')
    return if (dot > 0) {
        "${filename.substring(0, dot)}-$counter${filename.substring(dot)}"
    } else {
        "$filename-$counter"
    }
}
