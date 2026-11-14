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

/**
 * Decodes HTTP multipart field/file syntax without opening any referenced file.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal class CurlFormParser(
    private val bodies: CurlBodyParser,
    private val diagnostics: MutableList<ImportDiagnostic>
) {
    /**
     * Supports repeated -F parts and explicit type/filename metadata.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    fun parse(option: CurlOption): List<ImportPart> {
        val source = option.value!!
        val equals = source.indexOf('=')
        if (equals <= 0) fail(option, "A multipart field requires a nonempty name followed by '='.")
        val name = source.substring(0, equals)
        val content = source.substring(equals + 1)
        if (option.name == "--form-string") return listOf(ImportPart(name, ImportValue(content)))
        val segments = split(content, ';', option)
        val raw = segments.first().trim()
        if (raw.startsWith('<') || raw.startsWith('(') || raw == ")") {
            fail(option, "Multipart text-file reads and nested multipart syntax require manual conversion.")
        }
        val file = raw.startsWith('@')
        val attributes = attributes(segments.drop(1), option)
        if (!file && "filename" in attributes) fail(
            option,
            "Multipart text fields with filename metadata require manual conversion."
        )
        val value = if (file) fileValue(raw.drop(1), option) else ImportValue(unquote(raw, option))
        val contentType =
            attributes["type"] ?: if (file) fileContentType(value.text, attributes["filename"], option) else null
        return listOf(ImportPart(name, value, file, contentType, attributes["filename"]))
    }

    /**
     * Mirrors curl's filename-first inference from lib/mime.c without inspecting file bytes.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun fileContentType(path: String, filename: String?, option: CurlOption): String {
        for (candidate in listOfNotNull(filename, path)) {
            val known = fileContentTypes.entries.firstOrNull { candidate.endsWith(it.key, ignoreCase = true) }
            if (known != null) return known.value
        }
        val unknownExtension = listOfNotNull(filename, path).any { candidate ->
            candidate.substringAfterLast('/').substringAfterLast('\\').substringAfterLast('.', "").isNotEmpty()
        }
        if (unknownExtension) diagnostics += ImportDiagnostic(
            ImportSeverity.WARNING,
            "cURL:character ${option.offset + 1}",
            "File extension is outside the verified curl MIME mappings; application/octet-stream is used. Review the part type or provide ;type= explicitly.",
            requiresConsent = true
        )
        return "application/octet-stream"
    }

    /**
     * Multiple files use repeated -F options; comma lists have different multipart structure.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun fileValue(source: String, option: CurlOption): ImportValue {
        if (split(source, ',', option).size > 1) fail(
            option,
            "Multipart file lists require separate -F fields during import."
        )
        return bodies.filePath(option, unquote(source.trim(), option))
    }

    /**
     * Unknown part headers or transfer encodings cannot be dropped safely.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun attributes(segments: List<String>, option: CurlOption): Map<String, String> {
        val result = linkedMapOf<String, String>()
        for (segment in segments) {
            val name = segment.substringBefore('=').trim()
            if ('=' !in segment || name !in setOf("type", "filename")) {
                fail(option, "Unsupported multipart attribute; only type and filename can be imported.")
            }
            val value = unquote(segment.substringAfter('=').trim(), option)
            if (value.isEmpty()) fail(option, "Multipart metadata must have a value.")
            result[name] = value
        }
        return result
    }

    /**
     * Delimiters within curl's own quoted multipart values remain literal.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun split(source: String, delimiter: Char, option: CurlOption): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        var quoted = false
        var escaped = false
        for (index in source.indices) {
            val char = source[index]
            when {
                escaped -> escaped = false
                quoted && char == '\\' -> escaped = true
                char == '"' -> quoted = !quoted
                !quoted && char == delimiter -> {
                    result += source.substring(start, index)
                    start = index + 1
                }
            }
        }
        if (quoted || escaped) fail(option, "Unterminated quote in multipart field metadata.")
        result += source.substring(start)
        return result
    }

    /**
     * Curl multipart quoting removes escaped double quotes and backslashes.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun unquote(source: String, option: CurlOption): String {
        if (!source.startsWith('"')) return source
        if (!source.endsWith('"') || source.length < 2) fail(option, "Malformed multipart quoted value.")
        val result = StringBuilder()
        var index = 1
        while (index < source.lastIndex) {
            val char = source[index]
            if (char == '\\' && source.getOrNull(index + 1) in listOf('\\', '"')) {
                index++
                result.append(source[index])
            } else if (char == '"') {
                fail(option, "Unexpected text after a multipart quoted value.")
            } else {
                result.append(char)
            }
            index++
        }
        return result.toString()
    }

    /**
     * Reports metadata errors at the field option without echoing field content.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun fail(option: CurlOption, message: String): Nothing = throw CurlParseFailure(option.offset, message)

    /**
     * The stable suffix table verified against curl 8.10.1 lib/mime.c.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private companion object {
        val fileContentTypes = mapOf(
            ".gif" to "image/gif", ".jpg" to "image/jpeg", ".jpeg" to "image/jpeg", ".png" to "image/png",
            ".svg" to "image/svg+xml", ".txt" to "text/plain", ".htm" to "text/html", ".html" to "text/html",
            ".pdf" to "application/pdf", ".xml" to "application/xml"
        )
    }
}
