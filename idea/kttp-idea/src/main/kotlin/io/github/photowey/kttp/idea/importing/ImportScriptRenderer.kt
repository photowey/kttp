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

import io.github.photowey.kttp.core.enums.HttpMethod
import io.github.photowey.kttp.idea.gui.*
import java.nio.file.Path

/**
 * Reuses GUI generation and adds script-only method and file-body representations safely.
 * File content is read only when the resulting script is explicitly executed.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
object ImportScriptRenderer {
    fun render(request: ImportedRequest, existingScript: String = "", baseDirectory: String? = null): String {
        require(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+").matches(request.method)) { "Invalid HTTP method" }
        val draft = GuiDraft(
            "import-preview",
            request.name,
            DraftOwnership.DETACHED,
            ImportRequestAdapter.specification(request)
        )
        var source = GuiScriptGenerator.render(draft, existingScript, environmentKeys(request))
        if (request.method !in HttpMethod.entries.map { it.value }) {
            source = Regex("(?m)^GET\\(").replace(source) { "request(${ImportValues.quote(request.method)}, " }
        }
        val body = when (val b = request.body) {
            is ImportBody.File -> "    body(${fileExpression(b.path, request.variables, baseDirectory)}.readBytes())\n"
            is ImportBody.Multipart -> multipart(b, request.variables, baseDirectory)
            else -> ""
        }
        if (body.isNotEmpty()) {
            val end = source.lastIndexOf("}\n")
            source = source.substring(0, end) + body + source.substring(end)
        }
        return source
    }

    private fun environmentKeys(request: ImportedRequest): Set<String> = buildList {
        add(request.url)
        addAll(request.query.map { it.value })
        addAll(request.headers.map { it.value })
        when (val auth = request.auth) {
            ImportAuth.None -> Unit
            is ImportAuth.Basic -> {
                add(auth.username); add(auth.password)
            }

            is ImportAuth.Bearer -> add(auth.token)
            is ImportAuth.ApiKey -> add(auth.value)
        }
        when (val body = request.body) {
            ImportBody.None -> Unit
            is ImportBody.Text -> add(body.content)
            is ImportBody.Form -> addAll(body.fields.map { it.value })
            is ImportBody.File -> add(body.path)
            is ImportBody.Multipart -> addAll(body.parts.map { it.value })
        }
    }.flatMap { ImportValues.keys(it, request.variables) }.toSet()

    private fun multipart(body: ImportBody.Multipart, local: Map<String, String>, base: String?): String = buildString {
        append("    multipart {\n")
        body.parts.forEach { part ->
            require(part.name.none { it == '"' || it == '\\' || it.isISOControl() }) { "Unsupported multipart field name" }
            val contentType = part.contentType?.let { ", contentType = ${ImportValues.quote(it)}" }.orEmpty()
            append("        part(${ImportValues.quote(part.name)}$contentType) {\n")
            if (part.file) {
                append(
                    "            contentDisposition(args = listOf(\"name\" to ${ImportValues.quote(part.name)}, \"filename\" to ${
                        filename(
                            part,
                            local,
                            base
                        )
                    }))\n"
                )
                append("            body(${fileExpression(part.value, local, base)}.readBytes())\n")
            } else append("            body(${ImportValues.expression(part.value, local)})\n")
            append("        }\n")
        }
        append("    }\n")
    }

    private fun filename(part: ImportPart, local: Map<String, String>, base: String?): String {
        val resolved = ImportValues.resolve(part.value, local)
        val filename =
            part.filename ?: (resolved as? ValueRef.Literal)?.value?.replace('\\', '/')?.substringAfterLast('/')
        if (filename != null) {
            require(filename.none { it == '"' || it == '\\' || it.isISOControl() }) { "Unsupported multipart filename" }
            return ImportValues.quote(filename)
        }
        return "${
            fileExpression(
                part.value,
                local,
                base
            )
        }.name.also { require(it.none { ch -> ch.code == 34 || ch.code == 92 || ch.isISOControl() }) { \"Unsupported multipart filename\" } }"
    }

    private fun fileExpression(value: ImportValue, local: Map<String, String>, base: String?): String {
        val resolved = ImportValues.resolve(value, local)
        val expression = if (resolved is ValueRef.Literal) {
            val path = Path.of(resolved.value)
            require(path.isAbsolute || !base.isNullOrBlank()) { "Choose a base directory for relative upload files" }
            ImportValues.quote(
                if (path.isAbsolute) path.normalize().toString() else Path.of(base!!).resolve(path).normalize()
                    .toString()
            )
        } else ImportValues.expression(value, local)
        return "java.io.File($expression)"
    }
}
