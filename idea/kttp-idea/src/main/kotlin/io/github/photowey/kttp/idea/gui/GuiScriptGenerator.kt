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

import com.intellij.psi.TokenType
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens

/**
 * Renders a representable GUI request as Kotlin request DSL source.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
object GuiScriptGenerator {

    /**
     * Renders the request and required value declarations without writing files or executing Kotlin.
     * Existing script text is used to reuse compatible environment declarations and avoid property-name conflicts.
     */
    fun render(draft: GuiDraft, existingScript: String? = null): String {
        val spec = draft.request
        val bindings = ValueBindings(spec, existingScript.orEmpty())
        fun renderValue(value: ValueRef): String = bindings.render(value)
        val builder = StringBuilder()
        val method = spec.method.value
        val url = renderValue(spec.target.url)
        val nameArg = spec.identity.name?.takeIf { it.isNotBlank() }?.let {
            ", name = \"${escapeDoubleQuoted(it)}\""
        } ?: ""

        builder.append("$method($url$nameArg) {\n")

        spec.target.pathParams.forEach { pathParam ->
            builder.append("    path(\"${escapeDoubleQuoted(pathParam.name)}\", ${renderValue(pathParam.value)})\n")
        }

        spec.target.queryParams.forEach { param ->
            builder.append("    query(\"${escapeDoubleQuoted(param.name)}\", ${renderValue(param.value)})\n")
        }

        spec.headers.forEach { header ->
            builder.append("    header(\"${escapeDoubleQuoted(header.name)}\", ${renderValue(header.value)})\n")
        }

        builder.appendAuth(spec.auth, ::renderValue)
        builder.appendBody(spec, ::renderValue)

        if (spec.transport.noRedirect) {
            builder.append("    noRedirect()\n")
        }
        if (spec.transport.noCookies) {
            builder.append("    noCookies()\n")
        }
        if (spec.transport.http2PriorKnowledge) {
            builder.append("    http2()\n")
        }

        builder.append("}\n")
        return bindings.declarations.toString() + builder.toString()
    }

    private fun StringBuilder.appendAuth(auth: AuthSpec, renderValue: (ValueRef) -> String) {
        when (auth) {
            is AuthSpec.Inherit -> Unit
            is AuthSpec.Bearer -> {
                append("    bearerAuth(${renderValue(auth.token)})\n")
            }

            is AuthSpec.Basic -> {
                append("    basicAuth(${renderValue(auth.username)}, ${renderValue(auth.password)})\n")
            }

            is AuthSpec.ApiKey -> {
                when (auth.placement) {
                    ApiKeyPlacement.HEADER -> append(
                        "    header(\"${escapeDoubleQuoted(auth.keyName)}\", ${renderValue(auth.keyValue)})\n"
                    )

                    ApiKeyPlacement.QUERY -> append(
                        "    query(\"${escapeDoubleQuoted(auth.keyName)}\", ${renderValue(auth.keyValue)})\n"
                    )
                }
            }
        }
    }

    private fun StringBuilder.appendBody(spec: RequestSpec, renderValue: (ValueRef) -> String) {
        when (val body = spec.body) {
            is BodySpec.None -> Unit
            is BodySpec.Json -> {
                if (spec.headers.none { it.name.equals("Content-Type", ignoreCase = true) }) {
                    append("    contentType(\"application/json\")\n")
                }
                append("    body(${renderValue(body.content)})\n")
            }

            is BodySpec.Raw -> {
                body.contentType?.let {
                    append("    contentType(${renderValue(it)})\n")
                }
                append("    body(${renderValue(body.content)})\n")
            }

            is BodySpec.FormUrlEncoded -> {
                append("    formData {\n")
                body.fields.forEach { field ->
                    append("        field(\"${escapeDoubleQuoted(field.name)}\", ${renderValue(field.value)})\n")
                }
                append("    }\n")
            }
        }
    }

    fun defaultFileName(draft: GuiDraft): String {
        val base = draft.title.ifBlank { draft.request.method.value.lowercase() + "-request" }
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "request" }
        return "$base.kttp.kts"
    }

    /**
     * Creates script bindings for nonliteral request values while avoiding existing property names.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    private class ValueBindings(spec: RequestSpec, existingScript: String) {
        val declarations = StringBuilder()
        private val fileNames = linkedMapOf<ValueRef.LocalFile, String>()

        init {
            val existingProperties = existingProperties(existingScript)
            val usedNames = existingProperties.keys.toMutableSet()
            val values = buildList {
                add(spec.target.url)
                addAll(spec.target.pathParams.map { it.value })
                addAll(spec.target.queryParams.map { it.value })
                addAll(spec.headers.map { it.value })
                when (val auth = spec.auth) {
                    AuthSpec.Inherit -> Unit
                    is AuthSpec.Bearer -> add(auth.token)
                    is AuthSpec.Basic -> {
                        add(auth.username); add(auth.password)
                    }

                    is AuthSpec.ApiKey -> add(auth.keyValue)
                }
                when (val body = spec.body) {
                    BodySpec.None -> Unit
                    is BodySpec.Json -> add(body.content)
                    is BodySpec.Raw -> {
                        add(body.content); body.contentType?.let(::add)
                    }

                    is BodySpec.FormUrlEncoded -> addAll(body.fields.map { it.value })
                }
            }

            values.filterIsInstance<ValueRef.Env>().distinct().forEach { value ->
                val key = value.key
                require(key.isNotBlank() && key.none { it.isISOControl() || it in ".;[]/<>:\\`" } && key !in setOf(
                    "env",
                    "vars"
                )) {
                    "Environment key '$key' cannot be represented as a Kotlin script property. Use a key without JVM-forbidden characters or reserved DSL property names."
                }
                if (key in existingProperties) {
                    require(existingProperties.getValue(key)) {
                        "Script already declares '$key' without a String env delegate. Rename that property before adding this request."
                    }
                } else {
                    declarations.append("val `$key`: String by env\n")
                }
                usedNames += key
            }

            values.filterIsInstance<ValueRef.LocalFile>().distinct().forEach { value ->
                var index = 0
                while ("kttpFile$index" in usedNames) index++
                val name = "kttpFile$index"
                usedNames += name
                fileNames[value] = name
                declarations.append("val $name: String by localFile(\"${escapeDoubleQuoted(value.path)}\"")
                if (value.charset != "UTF-8") {
                    declarations.append(", charset = java.nio.charset.Charset.forName(\"${escapeDoubleQuoted(value.charset)}\")")
                }
                if (!value.required) declarations.append(", required = false")
                declarations.append(")\n")
            }
            if (declarations.isNotEmpty()) declarations.append('\n')
        }

        fun render(valueRef: ValueRef): String = when (valueRef) {
            is ValueRef.Literal -> "\"${escapeDoubleQuoted(valueRef.value)}\""
            is ValueRef.Env -> "`${valueRef.key}`"
            is ValueRef.LocalFile -> fileNames.getValue(valueRef)
            is ValueRef.OpaqueScript -> valueRef.raw
        }
    }

    /** Lexing avoids mistaking declarations inside comments or body strings for real script properties. */
    private fun existingProperties(script: String): Map<String, Boolean> {
        if (script.isBlank()) return emptyMap()
        /**
         * Records a lexer token and its nesting context while discovering existing script properties.
         *
         * @author photowey
         * @version 1.0.0
         * @since 2026/11/05
         */
        data class Token(
            val type: com.intellij.psi.tree.IElementType,
            val text: String,
            val end: Int,
            val topLevel: Boolean
        )

        val lexer = KotlinLexer().apply { start(script) }
        val tokens = mutableListOf<Token>()
        var braces = 0
        var parentheses = 0
        var brackets = 0
        var strings = 0
        while (lexer.tokenType != null) {
            val type = lexer.tokenType!!
            if (type != TokenType.WHITE_SPACE && !KtTokens.COMMENTS.contains(type)) {
                tokens += Token(
                    type,
                    lexer.tokenText,
                    lexer.tokenEnd,
                    braces == 0 && parentheses == 0 && brackets == 0 && strings == 0
                )
            }
            when (type) {
                KtTokens.LBRACE -> braces++
                KtTokens.RBRACE -> braces--
                KtTokens.LPAR -> parentheses++
                KtTokens.RPAR -> parentheses--
                KtTokens.LBRACKET -> brackets++
                KtTokens.RBRACKET -> brackets--
                KtTokens.OPEN_QUOTE -> strings++
                KtTokens.CLOSING_QUOTE -> strings--
            }
            lexer.advance()
        }
        val properties = linkedMapOf<String, Boolean>()
        tokens.forEachIndexed { index, token ->
            if (!token.topLevel || token.type !in setOf(
                    KtTokens.VAL_KEYWORD,
                    KtTokens.VAR_KEYWORD
                )
            ) return@forEachIndexed
            val name = tokens.getOrNull(index + 1)?.takeIf { it.type == KtTokens.IDENTIFIER }?.text
                ?.removeSurrounding("`") ?: return@forEachIndexed
            val declaration = tokens.drop(index + 2).takeWhile { it.topLevel }
            // `by` is a soft keyword: KotlinLexer returns IDENTIFIER until the parser remaps it.
            val byIndex = declaration.indexOfFirst { it.text == "by" }
            val type = declaration.take(byIndex.coerceAtLeast(0)).joinToString("") { it.text }
            val delegate = declaration.getOrNull(byIndex + 1)
            val remainder = delegate?.let { script.substring(it.end).substringBefore('\n').trim() }.orEmpty()
            val envString = token.type == KtTokens.VAL_KEYWORD && byIndex > 0 &&
                type in setOf(":String", ":kotlin.String") && delegate?.text == "env" &&
                (remainder.isEmpty() || remainder.startsWith(";") || remainder.startsWith("//"))
            properties[name] = envString && name !in properties
        }
        return properties
    }

    private fun escapeDoubleQuoted(input: String): String {
        return buildString(input.length) {
            input.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '$' -> {
                        append('\\')
                        append('$')
                    }

                    '\b' -> append("\\b")
                    '\t' -> append("\\t")
                    '\n' -> append("\\n")
                    '\u000C' -> append("\\u000c")
                    '\r' -> append("\\r")
                    else -> if (char.code < 0x20) {
                        append("\\u%04x".format(char.code))
                    } else {
                        append(char)
                    }
                }
            }
        }
    }
}
