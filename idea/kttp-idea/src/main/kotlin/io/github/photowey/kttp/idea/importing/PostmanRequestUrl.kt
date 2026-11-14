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

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Reads URL components while preserving raw percent encoding and query multiplicity.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal data class PostmanRequestUrl(
    val url: ImportValue, val query: List<ImportField> = emptyList(), val variables: Map<String, String> = emptyMap()
) {
    companion object {
        private val pathVariable = Regex("(?<=/):([A-Za-z0-9_.-]+)(?=/|$)")
        private val reference = Regex("\\{\\{[^{}]+}}")
        private val scheme = Regex("^([A-Za-z][A-Za-z0-9+.-]*):\\/\\/")

        fun read(
            source: JsonElement?,
            location: String,
            diagnostics: MutableList<ImportDiagnostic>
        ): PostmanRequestUrl {
            if (source is JsonPrimitive && source !is JsonNull && source.isString) {
                return checked(source.content, emptyList(), emptyMap(), location, diagnostics)
            }
            val obj = source as? JsonObject
            if (obj == null) {
                diagnostics += PostmanValues.error(location, "Request URL must be a string or object.", source)
                return PostmanRequestUrl(ImportValue(""))
            }
            val variables = PostmanValues.variables(obj["variable"], "$location.variable", diagnostics)
            val raw = PostmanValues.optionalText(obj, "raw", location, diagnostics)
            val query = queryEntries(obj["query"], "$location.query", diagnostics)
            if (raw != null) {
                val url = if (obj.containsKey("query")) applyRawQuery(raw, query) else raw
                return checked(url, emptyList(), variables, location, diagnostics)
            }
            val base = structured(obj, location, diagnostics)
            val active = query.filterNot(PostmanValues::disabled)
            val hasKeylessQuery = active.any { it["key"] is JsonNull || it["value"] is JsonNull || it["key"] == null }
            val url = if (hasKeylessQuery && active.isNotEmpty()) insertQuery(
                base,
                active.joinToString("&", transform = ::encodeQuery)
            ) else base
            val fields = if (hasKeylessQuery) emptyList() else active.map {
                ImportField(
                    PostmanValues.text(it["key"]).orEmpty(),
                    PostmanValues.value(PostmanValues.text(it["value"]).orEmpty())
                )
            }
            return checked(url, fields, variables, location, diagnostics)
        }

        private fun checked(
            raw: String, query: List<ImportField>, variables: Map<String, String>, location: String,
            diagnostics: MutableList<ImportDiagnostic>
        ): PostmanRequestUrl {
            val prefix = raw.substringBefore('#').substringBefore('?')
            val suffix = raw.removePrefix(prefix)
            val url = pathVariable.replace(prefix) { "{{${it.groupValues[1]}}}" } + suffix
            val protocol = scheme.find(url)?.groupValues?.get(1)
            when {
                url.isBlank() -> diagnostics += PostmanValues.error(location, "Request URL is empty.")
                protocol != null && protocol.lowercase() !in setOf("http", "https") -> diagnostics +=
                    PostmanValues.error(
                        location,
                        "Unsupported URL protocol '$protocol'; only HTTP and HTTPS can be imported."
                    )

                protocol == null && !url.startsWith("{{") -> diagnostics += PostmanValues.error(
                    location,
                    "Request URL requires an HTTP/HTTPS protocol or an unresolved base URL variable."
                )

                protocol != null && url.substringAfter("://").substringBefore('/').substringBefore('?')
                    .substringBefore('#').isBlank() ->
                    diagnostics += PostmanValues.error(location, "Request URL requires a host.")
            }
            return PostmanRequestUrl(PostmanValues.value(url), query, variables)
        }

        private fun structured(obj: JsonObject, location: String, diagnostics: MutableList<ImportDiagnostic>): String {
            val protocol = PostmanValues.optionalText(obj, "protocol", location, diagnostics)
            val host = component(obj["host"], ".", "$location.host", diagnostics)
            val path = component(obj["path"], "/", "$location.path", diagnostics, true)
            val port = PostmanValues.optionalText(obj, "port", location, diagnostics).orEmpty()
            val fragment = PostmanValues.optionalText(obj, "hash", location, diagnostics)
            return buildString {
                if (!protocol.isNullOrBlank()) append(protocol).append("://")
                append(host)
                if (port.isNotEmpty()) append(':').append(port)
                if (path.isNotEmpty()) append('/').append(path.removePrefix("/"))
                if (fragment != null) append('#').append(fragment.removePrefix("#"))
            }
        }

        private fun component(
            source: JsonElement?,
            separator: String,
            location: String,
            diagnostics: MutableList<ImportDiagnostic>,
            path: Boolean = false
        ): String {
            if (source == null || source is JsonNull) return ""
            if (source is JsonPrimitive && source.isString) return source.content
            if (source is JsonArray) return source.mapIndexed { index, item ->
                val text =
                    if (path && item is JsonObject) PostmanValues.text(item["value"]) else PostmanValues.text(item)
                if (text == null) diagnostics += PostmanValues.error(
                    "$location[$index]",
                    "URL component requires a string value.",
                    item
                )
                text.orEmpty()
            }.joinToString(separator)
            diagnostics += PostmanValues.error(location, "URL component must be a string or array.", source)
            return ""
        }

        private fun queryEntries(
            source: JsonElement?,
            location: String,
            diagnostics: MutableList<ImportDiagnostic>
        ): List<JsonObject> {
            if (source == null) return emptyList()
            if (source !is JsonArray) {
                diagnostics += PostmanValues.error(location, "URL query must be an array.", source)
                return emptyList()
            }
            return source.mapIndexedNotNull { index, item ->
                val obj = item as? JsonObject
                if (obj == null) diagnostics += PostmanValues.error(
                    "$location[$index]",
                    "Query entry must be an object.",
                    item
                )
                else if (!PostmanValues.disabled(obj) && listOf("key", "value").any {
                        val part = obj[it]
                        part != null && part !is JsonNull && (part !is JsonPrimitive || !part.isString)
                    }) diagnostics += PostmanValues.error(
                    "$location[$index]",
                    "Query keys and values must be strings or null.",
                    item
                )
                if (obj != null && !PostmanValues.disabled(obj)) PostmanValues.fieldName(
                    PostmanValues.text(obj["key"]), "$location[$index].key", "query parameter", diagnostics
                )
                obj
            }
        }

        private fun applyRawQuery(raw: String, query: List<JsonObject>): String {
            val prefix = raw.substringBefore('#')
            val tokens = if ('?' in prefix) prefix.substringAfter('?').split('&').toMutableList() else mutableListOf()
            val retained = query.mapNotNull { entry ->
                val key = PostmanValues.text(entry["key"])
                val value = PostmanValues.text(entry["value"])
                val match = tokens.indexOfFirst { token ->
                    same(token.substringBefore('='), key.orEmpty()) &&
                        same(if ('=' in token) token.substringAfter('=') else "", value.orEmpty())
                }
                val encoded = if (match >= 0) tokens.removeAt(match) else encodeQuery(entry)
                if (PostmanValues.disabled(entry)) null else encoded
            }
            return insertQuery(raw, retained.joinToString("&"))
        }

        private fun insertQuery(url: String, query: String): String {
            val fragment = if ('#' in url) "#${url.substringAfter('#')}" else ""
            return url.substringBefore('#').substringBefore('?') + (if (query.isEmpty()) "" else "?$query") + fragment
        }

        private fun same(raw: String, plain: String): Boolean = raw == plain || runCatching {
            URLDecoder.decode(raw, StandardCharsets.UTF_8) == plain
        }.getOrDefault(false)

        private fun encodeQuery(entry: JsonObject): String {
            val key = PostmanValues.text(entry["key"])
            val value = PostmanValues.text(entry["value"])
            return when {
                key == null -> encode(value.orEmpty())
                value == null -> encode(key)
                else -> "${encode(key)}=${encode(value)}"
            }
        }

        private fun encode(text: String): String = buildString {
            var offset = 0
            reference.findAll(text).forEach { match ->
                append(encodeLiteral(text.substring(offset, match.range.first)))
                append(match.value)
                offset = match.range.last + 1
            }
            append(encodeLiteral(text.substring(offset)))
        }

        private fun encodeLiteral(text: String): String =
            URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20")
    }
}
