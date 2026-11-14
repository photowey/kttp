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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parses Postman input without executing commands or requests.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
class PostmanImportParser : RequestImportParser {
    override fun parse(input: ImportInput): ImportBundle {
        val diagnostics = mutableListOf<ImportDiagnostic>()
        val environments = mutableListOf<ImportedEnvironment>()
        val primary = input.text.takeIf(String::isNotBlank)?.let { document(it, "Postman", diagnostics) }
        if (!input.environmentText.isNullOrBlank()) {
            document(input.environmentText, "Environment", diagnostics)?.let {
                environment(
                    it,
                    "Environment",
                    diagnostics
                )
            }
                ?.let(environments::add)
        }
        if (primary == null) {
            if (input.text.isBlank() && environments.isEmpty() && diagnostics.isEmpty()) {
                diagnostics += PostmanValues.error("Postman", "Select a Collection or Environment JSON document.")
            }
            return bundle(environments = environments, diagnostics = diagnostics)
        }
        if ("values" in primary && "item" !in primary && "info" !in primary) {
            environment(primary, "Environment", diagnostics)?.let(environments::add)
            return bundle(environments = environments, diagnostics = diagnostics)
        }
        val info = primary["info"] as? JsonObject
        val schema = PostmanValues.text(info?.get("schema"))
        if (schema == null || !SCHEMA.matches(schema)) {
            diagnostics += PostmanValues.error(
                "Collection.info.schema",
                "Only Postman Collection v2.0 and v2.1 JSON schemas are supported."
            )
            return bundle(environments = environments, diagnostics = diagnostics)
        }
        val name = PostmanValues.text(info?.get("name"))?.takeIf(String::isNotBlank) ?: "Collection"
        val variables = PostmanValues.variables(primary["variable"], "$name.variable", diagnostics)
        val inherited = scope(primary, PostmanScope(), name, false)
        val requests = mutableListOf<ImportedRequest>()
        val known = variables + environments.flatMap { it.values.entries }.associate { it.key to it.value }
        walk(primary["item"], name, inherited, known, requests, diagnostics, 0)
        return bundle(requests, variables, environments, diagnostics).copy(sourceName = name)
    }

    private fun bundle(
        requests: List<ImportedRequest> = emptyList(), variables: Map<String, String> = emptyMap(),
        environments: List<ImportedEnvironment>, diagnostics: List<ImportDiagnostic>
    ): ImportBundle = ImportBundle(
        requests, variables, environments,
        diagnostics + PostmanValues.environmentDiagnostics(variables, environments),
        sourceFormat = ImportFormat.POSTMAN
    )

    private fun document(text: String, location: String, diagnostics: MutableList<ImportDiagnostic>): JsonObject? {
        if (text.length > MAX_DOCUMENT_SIZE || tooDeep(text)) {
            diagnostics += PostmanValues.error(location, "Postman JSON exceeds the import size or nesting limit.")
            return null
        }
        val document = try {
            Json.parseToJsonElement(text)
        } catch (_: IllegalArgumentException) {
            diagnostics += PostmanValues.error(location, "Invalid JSON document; check JSON syntax.")
            return null
        }
        if (document !is JsonObject) diagnostics += PostmanValues.error(
            location,
            "Postman document must be a JSON object."
        )
        return document as? JsonObject
    }

    private fun tooDeep(text: String): Boolean {
        var depth = 0
        var quoted = false
        var escaped = false
        for (char in text) {
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> if (++depth > MAX_JSON_DEPTH) return true
                '}', ']' -> depth--
            }
        }
        return false
    }

    private fun environment(
        root: JsonObject, location: String, diagnostics: MutableList<ImportDiagnostic>
    ): ImportedEnvironment? {
        val type = PostmanValues.text(root["_postman_variable_scope"])
        if ((type != null && type != "environment") || root["values"] !is JsonArray) {
            diagnostics += PostmanValues.error(
                location,
                "Expected a Postman Environment JSON object with a values array."
            )
            return null
        }
        val values = PostmanValues.variables(root["values"], "$location.values", diagnostics)
        val name = PostmanValues.text(root["name"])?.takeIf(String::isNotBlank) ?: "Imported environment"
        return ImportedEnvironment(name, values)
    }

    private fun scope(
        node: JsonObject,
        inherited: PostmanScope,
        path: String,
        includeVariables: Boolean = true
    ): PostmanScope {
        val diagnostics = inherited.diagnostics.toMutableList()
        val auth = PostmanAuthentication.read(node["auth"], inherited.auth, "$path.auth")
        val variables = inherited.variables + if (includeVariables) {
            PostmanValues.variables(node["variable"], "$path.variable", diagnostics)
        } else emptyMap()
        diagnostics += PostmanValues.events(node["event"], "$path.event")
        val noRedirect = PostmanRequestOptions.profile(
            node["protocolProfileBehavior"], inherited.noRedirect,
            "$path.protocolProfileBehavior", diagnostics
        )
        return PostmanScope(auth, variables, diagnostics, noRedirect)
    }

    private fun walk(
        source: JsonElement?, path: String, inherited: PostmanScope, known: Map<String, String>,
        requests: MutableList<ImportedRequest>, diagnostics: MutableList<ImportDiagnostic>, depth: Int
    ) {
        if (depth > MAX_FOLDER_DEPTH || source !is JsonArray) {
            diagnostics += PostmanValues.error(
                "$path.item",
                "Collection items require an array within the folder nesting limit."
            )
            return
        }
        source.forEachIndexed { index, item ->
            if (requests.size >= MAX_REQUESTS) {
                if (diagnostics.none { it.location == "Collection.item.limit" }) diagnostics += PostmanValues.error(
                    "Collection.item.limit",
                    "Collection exceeds the maximum supported number of requests ($MAX_REQUESTS)."
                )
                return
            }
            val node = item as? JsonObject
            val location = "$path.item[$index]"
            if (node == null) diagnostics += PostmanValues.error(location, "Collection item must be an object.", item)
            else visit(node, location, path, inherited, known, requests, diagnostics, depth)
        }
    }

    private fun visit(
        node: JsonObject, location: String, path: String, inherited: PostmanScope, known: Map<String, String>,
        requests: MutableList<ImportedRequest>, diagnostics: MutableList<ImportDiagnostic>, depth: Int
    ) {
        val name = PostmanValues.text(node["name"])?.takeIf(String::isNotBlank) ?: "Request ${requests.size + 1}"
        val itemPath = "$path/$name"
        val local = scope(node, inherited, itemPath)
        when {
            "request" in node && "item" in node -> diagnostics += PostmanValues.error(
                location,
                "An item cannot be both a request and a folder.",
                node
            )

            "request" in node -> requests += request(node["request"], name, itemPath, local, known)
            "item" in node -> walk(node["item"], itemPath, local, known, requests, diagnostics, depth + 1)
            else -> diagnostics += PostmanValues.error(
                location,
                "Collection item requires a request or child items.",
                node
            )
        }
    }

    private fun request(
        source: JsonElement?, name: String, path: String, inherited: PostmanScope, known: Map<String, String>
    ): ImportedRequest {
        val obj = when {
            source is JsonObject -> source
            source is JsonPrimitive && source.isString -> JsonObject(
                mapOf(
                    "url" to source,
                    "method" to JsonPrimitive("GET")
                )
            )

            else -> null
        }
        val local = if (obj != null) scope(obj, inherited, "$path.request") else inherited
        val diagnostics = (local.diagnostics + local.auth.diagnostics).toMutableList()
        if (obj == null) diagnostics += PostmanValues.error(path, "Request must be an object or URL string.", source)
        val method = PostmanValues.text(obj?.get("method")).orEmpty()
        if (!METHOD.matches(method)) diagnostics += PostmanValues.error(
            "$path.request.method",
            "Request requires a valid HTTP method token."
        )
        val url = PostmanRequestUrl.read(obj?.get("url"), "$path.request.url", diagnostics)
        val headers = PostmanRequestOptions.headers(obj?.get("header"), "$path.request.header", diagnostics)
        val body = PostmanRequestBody.read(obj?.get("body"), headers, "$path.request.body", diagnostics)
        if (body != ImportBody.None && method in setOf("GET", "HEAD")) diagnostics += PostmanValues.error(
            "$path.request.body",
            "$method request bodies are unsupported by Kttp/OkHttp; remove the body before importing."
        )
        if (obj != null) diagnostics += PostmanRequestOptions.unsupported(obj, "$path.request")
        val result = ImportedRequest(
            name, method, url.url, headers, url.query, local.auth.value, body,
            local.noRedirect, path, local.variables + url.variables, diagnostics
        )
        val references = PostmanValues.references(PostmanValues.requestValues(result), known + result.variables, path)
        return result.copy(diagnostics = diagnostics + references)
    }

    companion object {
        private val SCHEMA =
            Regex("^https?://schema\\.(?:getpostman|postman)\\.com/(?:json/collection/|collection/json/)v2\\.[01]\\.0/(?:draft-0[47]/)?collection\\.json$")
        private val METHOD = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
        private const val MAX_DOCUMENT_SIZE = 5_000_000
        private const val MAX_JSON_DEPTH = 100
        private const val MAX_FOLDER_DEPTH = 32
        private const val MAX_REQUESTS = 10_000
    }
}

/**
 * Inherited collection/folder state, kept separate from bundle default variables.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
private data class PostmanScope(
    val auth: PostmanAuthentication = PostmanAuthentication(),
    val variables: Map<String, String> = emptyMap(),
    val diagnostics: List<ImportDiagnostic> = emptyList(),
    val noRedirect: Boolean = false
)
