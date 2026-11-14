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

package io.github.photowey.kttp.idea.importing

import java.nio.file.InvalidPathException
import java.nio.file.Paths

/**
 * A body plus query data that --get moves to the URL; no bytes are read from files.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal data class CurlBodyResult(val body: ImportBody, val queryData: String? = null, val json: Boolean = false)

/**
 * Preserves payload encoding and file references for the supported cURL data options.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal class CurlBodyParser(
    private val baseDirectory: String?,
    private val diagnostics: MutableList<ImportDiagnostic>
) {
    /**
     * Converts one body family; unsupported combinations fail instead of losing data.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    fun parse(options: List<CurlOption>, get: Boolean, head: Boolean): CurlBodyResult {
        val data = options.filter { it.name in dataOptions }
        val forms = options.filter { it.name == "--form" || it.name == "--form-string" }
        if (forms.isNotEmpty() && (data.isNotEmpty() || head || get)) {
            fail(forms.first(), "Multipart cannot be combined with data, HEAD or --get during import.")
        }
        if (head && data.isNotEmpty() && !get) fail(data.first(), "HEAD and request data require --get in cURL.")
        if (forms.isNotEmpty()) return CurlBodyResult(
            ImportBody.Multipart(forms.flatMap { CurlFormParser(this, diagnostics).parse(it) })
        )
        if (data.isEmpty()) return CurlBodyResult(ImportBody.None)
        val json = data.any { it.name == "--json" }
        if (json && data.any { it.name != "--json" }) fail(
            data.first(),
            "Mixed --json and other data options require manual conversion."
        )
        if (data.any { isFile(it) }) return fileData(data, get, json)
        val text = buildString {
            for ((index, option) in data.withIndex()) {
                if (index > 0 && !json) append('&')
                append(dataValue(option))
            }
        }
        if (get) return CurlBodyResult(ImportBody.None, text, json)
        return CurlBodyResult(
            ImportBody.Text(
                ImportValue(text),
                if (json) "application/json" else "application/x-www-form-urlencoded"
            ), json = json
        )
    }

    /**
     * Resolves a path syntactically against the explicit source directory without I/O.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    fun filePath(option: CurlOption, path: String): ImportValue {
        if (path.isEmpty() || path == "-") fail(option, "Missing upload path or stdin input is not supported.")
        try {
            val parsed = Paths.get(path)
            val resolved =
                if (parsed.isAbsolute || baseDirectory == null) parsed else Paths.get(baseDirectory).resolve(parsed)
            return ImportValue(resolved.normalize().toString())
        } catch (_: InvalidPathException) {
            fail(option, "The upload path is invalid on this host.")
        }
    }

    /**
     * Text-file transformations cannot be represented by a binary file reference.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun fileData(data: List<CurlOption>, get: Boolean, json: Boolean): CurlBodyResult {
        val option = data.first { isFile(it) }
        if (option.name !in setOf("--data-binary", "--json")) {
            fail(
                option,
                "This data option transforms file content; import supports file references with --data-binary or --json."
            )
        }
        if (data.size != 1 || get) fail(
            option,
            "Combining file data or moving file data to a query requires reading it and is not supported."
        )
        return CurlBodyResult(ImportBody.File(filePath(option, option.value!!.drop(1))), json = json)
    }

    /**
     * Detects file-content syntax only for options that curl treats specially.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun isFile(option: CurlOption): Boolean {
        val value = option.value!!
        if (option.name == "--data-raw") return false
        if (option.name == "--data-urlencode") return '=' !in value && '@' in value
        return value.startsWith('@')
    }

    /**
     * Existing raw payload escapes are left unchanged; only --data-urlencode is encoded.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun dataValue(option: CurlOption): String {
        val value = option.value!!
        if (option.name != "--data-urlencode") return value
        if ('=' !in value) return encode(value)
        val name = value.substringBefore('=')
        val content = encode(value.substringAfter('='))
        return if (name.isEmpty()) content else "$name=$content"
    }

    /**
     * Encodes UTF-8 bytes exactly like curl's URL escaping, including %20 for spaces.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun encode(value: String): String = buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val number = byte.toInt() and 0xff
            if (number in 65..90 || number in 97..122 || number in 48..57 || number.toChar() in "-._~") {
                append(number.toChar())
            } else {
                append('%')
                append("0123456789ABCDEF"[number ushr 4])
                append("0123456789ABCDEF"[number and 15])
            }
        }
    }

    /**
     * Creates safe diagnostics without including user data or filesystem paths.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun fail(option: CurlOption, message: String): Nothing = throw CurlParseFailure(option.offset, message)

    /**
     * The data family shares curl's ordered ampersand joining, except for JSON concatenation.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private companion object {
        val dataOptions = setOf("--data", "--data-ascii", "--data-raw", "--data-binary", "--data-urlencode", "--json")
    }
}
