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

import io.github.photowey.kttp.idea.gui.ValueRef

/**
 * Escapes imported text and converts explicit Postman variables without executing expressions.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal object ImportValues {
    private val variable = Regex("\\{\\{([^{}]+)}}")

    fun quote(text: String): String = "\"" + buildString {
        text.forEach { c ->
            append(
                when (c) {
                    '\\' -> "\\\\"; '"' -> "\\\""; '$' -> "\\$"; '\n' -> "\\n"; '\r' -> "\\r"; '\t' -> "\\t"
                    else -> if (c.isISOControl()) "\\u%04x".format(c.code) else c.toString()
                }
            )
        }
    } + "\""

    fun resolve(value: ImportValue, local: Map<String, String>): ValueRef {
        if (!value.interpolate) return ValueRef.Literal(value.text)
        val expanded = expanded(value.text, local)
        val matches = variable.findAll(expanded).toList()
        if (matches.isEmpty()) return ValueRef.Literal(expanded)
        matches.forEach { validateKey(it.groupValues[1]) }
        if (matches.size == 1 && matches.single().value == expanded) return ValueRef.Env(matches.single().groupValues[1])
        val template = StringBuilder("\"")
        var start = 0
        matches.forEach {
            template.append(quote(expanded.substring(start, it.range.first)).removeSurrounding("\""))
            val key = it.groupValues[1]
            val identifier =
                if (Regex("[A-Za-z_][A-Za-z0-9_]*").matches(key) && key !in kotlinKeywords) key else "`$key`"
            template.append("\${").append(identifier).append("}")
            start = it.range.last + 1
        }
        template.append(quote(expanded.substring(start)).removeSurrounding("\""))
        return ValueRef.OpaqueScript(template.append('"').toString())
    }

    fun keys(value: ImportValue, local: Map<String, String>): Set<String> {
        if (!value.interpolate) return emptySet()
        return variable.findAll(expanded(value.text, local)).map { it.groupValues[1] }
            .onEach(::validateKey).toSet()
    }

    fun expression(value: ImportValue, local: Map<String, String>): String = when (val ref = resolve(value, local)) {
        is ValueRef.Literal -> quote(ref.value)
        is ValueRef.Env -> "`${ref.key}`"
        is ValueRef.OpaqueScript -> ref.raw
        is ValueRef.LocalFile -> error("Unexpected file reference")
    }

    private val kotlinKeywords = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
        "if", "in", "interface", "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try",
        "typealias", "typeof", "val", "var", "when", "while"
    )

    private fun expanded(text: String, local: Map<String, String>): String =
        requireNotNull(ImportVariableExpansion(local, preserveMissing = true).expand(text)) {
            "Cyclic or excessively nested local variable reference"
        }

    private fun validateKey(key: String) {
        require(key.isNotBlank() && key.none { it.isISOControl() || it in ".;[]/<>:\\`" } && key !in setOf(
            "env",
            "vars"
        )) {
            "Variable '$key' cannot be represented by a Kotlin environment delegate; rename it before importing"
        }
    }
}
