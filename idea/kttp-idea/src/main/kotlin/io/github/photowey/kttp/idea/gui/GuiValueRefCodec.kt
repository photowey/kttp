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

package io.github.photowey.kttp.idea.gui

import kotlinx.serialization.json.Json

/**
 * A leading backslash forces literal text, including whitespace and reference markers.
 * Display escapes only ambiguous literals and leading backslashes. Ordinary env/file
 * references retain their shorthand; @ref: JSON preserves metadata shorthand cannot hold.
 * Invalid or disallowed reference input always remains literal.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
object GuiValueRefCodec {
    private val envRegex = Regex("""\$\{env:([A-Za-z0-9_.-]+)}""")
    private const val filePrefix = "@file:"
    private const val referencePrefix = "@ref:"
    private const val literalEscape = "\\"

    fun toDisplayValue(valueRef: ValueRef): String = when (valueRef) {
        is ValueRef.Literal -> displayLiteral(valueRef.value)
        is ValueRef.Env -> displayReference(valueRef, "\${env:${valueRef.key}}", allowLocalFile = false)
        is ValueRef.LocalFile -> displayReference(valueRef, "$filePrefix${valueRef.path}", allowLocalFile = true)
        is ValueRef.OpaqueScript -> valueRef.raw
    }

    /**
     * Recognizes supported editor references while preserving literal whitespace and explicit literal escapes.
     * Local-file references are recognized only where allowed; unrecognized input remains a literal.
     */
    fun parse(input: String, allowLocalFile: Boolean = false): ValueRef {
        if (input.startsWith(literalEscape)) return ValueRef.Literal(input.removePrefix(literalEscape))
        return parseReference(input.trim(), allowLocalFile) ?: ValueRef.Literal(input)
    }

    private fun displayLiteral(value: String): String =
        if (value.startsWith(literalEscape) || parseReference(value.trim(), allowLocalFile = true) != null) {
            literalEscape + value
        } else value

    private fun displayReference(value: ValueRef, shorthand: String, allowLocalFile: Boolean): String =
        if (parseReference(shorthand.trim(), allowLocalFile) == value) shorthand
        else referencePrefix + Json.encodeToString(ValueRef.serializer(), value)

    private fun parseReference(input: String, allowLocalFile: Boolean): ValueRef? {
        envRegex.matchEntire(input)?.let { return ValueRef.Env(it.groupValues[1]) }
        if (allowLocalFile && input.startsWith(filePrefix) && input.length > filePrefix.length) {
            return ValueRef.LocalFile(input.removePrefix(filePrefix))
        }
        if (!input.startsWith(referencePrefix)) return null
        val decoded = runCatching {
            Json.decodeFromString(ValueRef.serializer(), input.removePrefix(referencePrefix))
        }.getOrNull()
        return decoded?.takeIf { it is ValueRef.Env || (allowLocalFile && it is ValueRef.LocalFile) }
    }
}
