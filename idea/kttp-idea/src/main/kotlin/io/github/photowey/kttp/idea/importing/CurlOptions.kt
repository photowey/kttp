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
 * An allowed canonical option; positional URLs use the same shape as --url.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal data class CurlOption(val name: String, val value: String?, val offset: Int)

/**
 * Decodes only explicitly supported options, including short clusters and attached values.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal class CurlOptions(private val tokens: List<CurlToken>) {
    private val result = mutableListOf<CurlOption>()
    private var index = 1
    private var positional = false

    /**
     * Rejects unknown options rather than guessing their argument count or meaning.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    fun parse(): List<CurlOption> {
        val executable = tokens.firstOrNull()?.text?.substringAfterLast('/')
        if (executable != "curl") fail(0, "Input must contain one POSIX cURL command beginning with curl.")
        while (index < tokens.size) {
            val token = tokens[index]
            when {
                positional || !token.text.startsWith('-') || token.text == "-" -> add("--url", token.text, token.offset)
                token.text == "--" -> positional = true
                token.text.startsWith("--") -> longOption(token)
                else -> shortOptions(token)
            }
            index++
        }
        return result
    }

    /**
     * Long options permit an equals-attached argument but never option abbreviations.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun longOption(token: CurlToken) {
        val name = token.text.substringBefore('=')
        if (name !in valued && name !in flags) fail(
            token.offset,
            "Unsupported cURL option; its request semantics cannot be imported."
        )
        val attached = token.text.takeIf { '=' in it }?.substringAfter('=')
        if (name in flags && attached != null) fail(token.offset, "This cURL switch does not accept an argument.")
        add(name, if (name in valued) attached ?: nextValue(token.offset) else null, token.offset)
    }

    /**
     * A value-taking short option consumes the remainder of its cluster or the next token.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun shortOptions(token: CurlToken) {
        var position = 1
        while (position < token.text.length) {
            val name = shortNames[token.text[position]] ?: fail(token.offset, "Unsupported short cURL option.")
            if (name in valued) {
                val value = token.text.substring(position + 1).ifEmpty { nextValue(token.offset) }
                add(name, value, token.offset)
                return
            }
            add(name, null, token.offset)
            position++
        }
    }

    /**
     * Missing arguments fail at the corresponding option instead of raising an index error.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun nextValue(offset: Int): String {
        index++
        return tokens.getOrNull(index)?.text ?: fail(offset, "Missing argument for cURL option.")
    }

    /**
     * Bounds expanded short clusters as well as lexer argument counts.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun add(name: String, value: String?, offset: Int) {
        if (result.size >= 16_384) fail(offset, "Command exceeds the 16,384 option import limit.")
        result += CurlOption(name, value, offset)
    }

    /**
     * Produces a location-only failure without including option argument text.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun fail(offset: Int, message: String): Nothing = throw CurlParseFailure(offset, message)

    /**
     * The bounded option vocabulary is deliberately smaller than curl's command language.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private companion object {
        val valued = setOf(
            "--url", "--request", "--header", "--cookie", "--user", "--oauth2-bearer",
            "--data", "--data-ascii", "--data-raw", "--data-binary", "--data-urlencode", "--json",
            "--form", "--form-string", "--user-agent", "--referer", "--output", "--dump-header", "--write-out"
        )
        val flags = setOf(
            "--head", "--no-head", "--get", "--no-get", "--location", "--no-location", "--globoff", "--no-globoff",
            "--basic", "--silent", "--no-silent", "--show-error", "--no-show-error",
            "--verbose", "--no-verbose", "--include", "--no-include", "--show-headers", "--no-show-headers",
            "--fail", "--no-fail", "--fail-with-body", "--no-fail-with-body", "--no-progress-meter", "--progress-meter",
            "--progress-bar", "--no-progress-bar", "--disable", "--compressed", "--no-compressed"
        )
        val shortNames = mapOf(
            'X' to "--request", 'H' to "--header", 'b' to "--cookie", 'u' to "--user", 'd' to "--data",
            'F' to "--form", 'A' to "--user-agent", 'e' to "--referer", 'o' to "--output", 'D' to "--dump-header",
            'w' to "--write-out", 'I' to "--head", 'G' to "--get", 'L' to "--location", 'g' to "--globoff",
            's' to "--silent", 'S' to "--show-error", 'v' to "--verbose", 'i' to "--include", 'f' to "--fail",
            '#' to "--progress-bar", 'q' to "--disable"
        )
    }
}
