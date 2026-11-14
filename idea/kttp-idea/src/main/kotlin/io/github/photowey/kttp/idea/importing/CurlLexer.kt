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
 * One statically decoded shell argument with a diagnostic offset into the command.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal data class CurlToken(val text: String, val offset: Int)

/**
 * A bounded POSIX argument lexer; shell execution and expansion are never performed.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal class CurlLexer(private val source: String) {
    private val tokens = mutableListOf<CurlToken>()
    private val word = StringBuilder()
    private var index = 0
    private var start = -1
    private var quote: Char? = null
    private var commandEnded = false

    /**
     * Returns arguments or a location-only error without copying credential text.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    fun tokenize(): List<CurlToken> {
        if (source.length > 1_048_576) fail("Command exceeds the 1 MiB import limit.")
        val nul = source.indexOf('\u0000')
        if (nul >= 0) throw CurlParseFailure(nul, "NUL characters are not supported.")
        while (index < source.length) {
            val char = source[index]
            if (char == '\u0000') fail("NUL characters are not supported.")
            when {
                quote == '\'' -> singleQuoted(char)
                quote == '"' -> doubleQuoted(char)
                char == '\\' -> escaped(false)
                char == '\'' || char == '"' -> beginQuote(char)
                char == '\n' || char == '\r' -> endLine()
                char == ' ' || char == '\t' -> flush()
                char in ";|&<>()`" -> fail("Shell operators and command substitution are not supported.")
                char == '$' -> fail("Shell expansion is not supported; use a quoted literal value.")
                char == '#' && start < 0 -> fail("Shell comments are not supported.")
                char == '~' && start < 0 -> fail("Shell home-directory expansion is not supported.")
                char in "*?[" -> fail("Shell filename expansion is not supported; quote literal URL and data values.")
                else -> append(char)
            }
            index++
        }
        if (quote != null) fail("Unterminated shell quote.")
        flush()
        return tokens
    }

    /**
     * Single quotes make every character except the closing quote literal.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun singleQuoted(char: Char) {
        if (char == '\'') quote = null else word.append(char)
    }

    /**
     * Double quotes follow POSIX backslash rules and reject executable expansion.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun doubleQuoted(char: Char) {
        when {
            char == '"' -> quote = null
            char == '\\' -> escaped(true)
            char == '`' -> fail("Command substitution is not supported.")
            char == '$' && startsExpansion() -> fail("Shell expansion is not supported; use a quoted literal value.")
            else -> word.append(char)
        }
    }

    /**
     * Recognizes actual expansion syntax while allowing ordinary currency literals.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun startsExpansion(): Boolean {
        val next = source.getOrNull(index + 1) ?: return false
        return next.isLetterOrDigit() || next in "_({?*!@#$-"
    }

    /**
     * Backslash-newline is removed; double-quoted ordinary backslashes are retained.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun escaped(inDoubleQuotes: Boolean) {
        val next = source.getOrNull(index + 1) ?: fail("Trailing shell escape.")
        if (next == '\n') {
            index++
        } else if (next == '\r' && source.getOrNull(index + 2) == '\n') {
            index += 2
        } else if (inDoubleQuotes && next !in "\$`\"\\") {
            word.append('\\')
        } else {
            append(next)
            index++
        }
    }

    /**
     * Starts a quoted segment, including an intentionally empty argument.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun beginQuote(char: Char) {
        ensureWord()
        quote = char
    }

    /**
     * A raw newline terminates a command; only trailing whitespace may follow.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun endLine() {
        flush()
        if (tokens.isNotEmpty()) commandEnded = true
    }

    /**
     * Appends a literal character to the current argument.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun append(char: Char) {
        ensureWord()
        word.append(char)
    }

    /**
     * Tracks token position and prevents a second shell command.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun ensureWord() {
        if (commandEnded) fail("Only one cURL command is supported; use backslash line continuations.")
        if (start < 0) start = index
    }

    /**
     * Ends an argument without losing explicitly quoted empty values.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun flush() {
        if (start < 0) return
        if (tokens.size >= 16_384) fail("Command exceeds the 16,384 argument import limit.")
        tokens += CurlToken(word.toString(), start)
        word.setLength(0)
        start = -1
    }

    /**
     * Fails without echoing any part of the user command.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun fail(message: String): Nothing = throw CurlParseFailure(index, message)
}

/**
 * A static parse failure carrying only a source offset and safe explanation.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal class CurlParseFailure(val offset: Int, message: String) : IllegalArgumentException(message)
