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

/**
 * Reads supported body modes while retaining file references without file access.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal object PostmanRequestBody {
    fun read(
        source: JsonElement?, headers: List<ImportField>, location: String, diagnostics: MutableList<ImportDiagnostic>
    ): ImportBody {
        if (source == null || source is JsonNull) return ImportBody.None
        val body = source as? JsonObject
        if (body == null) {
            diagnostics += PostmanValues.error(location, "Request body must be an object.", source)
            return ImportBody.None
        }
        if (PostmanValues.disabled(body)) return ImportBody.None
        options(body["options"], "$location.options", diagnostics)
        return when (val mode = PostmanValues.text(body["mode"])) {
            "raw" -> raw(body, headers, location, diagnostics)
            "urlencoded" -> ImportBody.Form(fields(body[mode], "$location.$mode", diagnostics))
            "formdata" -> ImportBody.Multipart(multipart(body[mode], "$location.$mode", diagnostics))
            "file" -> file(body[mode], location, diagnostics)
            else -> {
                diagnostics += PostmanValues.error(
                    location,
                    "Unsupported or missing Postman body mode '${mode.orEmpty()}'.",
                    source
                )
                ImportBody.None
            }
        }
    }

    private fun options(source: JsonElement?, location: String, diagnostics: MutableList<ImportDiagnostic>) {
        if (source == null || source is JsonNull) return
        val options = source as? JsonObject
        if (options == null) {
            diagnostics += PostmanValues.error(location, "Body options must be an object.", source)
            return
        }
        val raw = options["raw"] as? JsonObject
        if (options.keys.any { it != "raw" } || raw?.keys?.any { it != "language" } == true) {
            diagnostics += PostmanValues.warning(
                location,
                "Unsupported Postman body options require manual review.",
                source
            )
        }
        if ("raw" in options && raw == null) diagnostics += PostmanValues.error(
            location,
            "Raw body options must be an object.",
            source
        )
        if (raw != null) PostmanValues.optionalText(raw, "language", "$location.raw", diagnostics)
    }

    private fun raw(
        body: JsonObject, headers: List<ImportField>, location: String, diagnostics: MutableList<ImportDiagnostic>
    ): ImportBody {
        val content = PostmanValues.text(body["raw"])
        if (content == null) diagnostics += PostmanValues.error(location, "Raw body requires string content.", body)
        val options = body["options"] as? JsonObject
        val rawOptions = options?.get("raw") as? JsonObject
        val language = PostmanValues.text(rawOptions?.get("language"))
        val type = headers.lastOrNull { it.name.equals("Content-Type", true) }?.value?.text
            ?: when (language) {
                "json" -> "application/json"
                "xml" -> "application/xml"
                "html" -> "text/html"
                "javascript" -> "application/javascript"
                else -> "text/plain"
            }
        return ImportBody.Text(PostmanValues.value(content.orEmpty()), type)
    }

    fun fields(source: JsonElement?, location: String, diagnostics: MutableList<ImportDiagnostic>): List<ImportField> =
        entries(source, location, diagnostics).mapNotNull { (index, entry) ->
            val key = PostmanValues.text(entry["key"])
            PostmanValues.fieldName(key, "$location[$index].key", "form field", diagnostics)
            val value = PostmanValues.optionalText(entry, "value", "$location[$index]", diagnostics) ?: ""
            if (key == null) {
                diagnostics += PostmanValues.error("$location[$index]", "Enabled field requires a key.", entry)
                null
            } else ImportField(key, PostmanValues.value(value))
        }

    private fun entries(
        source: JsonElement?, location: String, diagnostics: MutableList<ImportDiagnostic>
    ): List<Pair<Int, JsonObject>> {
        if (source !is JsonArray) {
            diagnostics += PostmanValues.error(location, "Body fields must be an array.", source)
            return emptyList()
        }
        return source.mapIndexedNotNull { index, item ->
            val entry = item as? JsonObject
            when {
                entry == null -> {
                    diagnostics += PostmanValues.error("$location[$index]", "Body field must be an object.", item)
                    null
                }

                PostmanValues.disabled(entry) -> null
                else -> index to entry
            }
        }
    }

    private fun multipart(
        source: JsonElement?,
        location: String,
        diagnostics: MutableList<ImportDiagnostic>
    ): List<ImportPart> =
        entries(source, location, diagnostics).flatMap { (index, entry) ->
            val path = "$location[$index]"
            val key = PostmanValues.text(entry["key"])
            PostmanValues.fieldName(key, "$path.key", "multipart field", diagnostics)
            if (key == null) {
                diagnostics += PostmanValues.error(path, "Multipart field requires a key.", entry)
                emptyList()
            } else readParts(key, entry, path, diagnostics)
        }

    private fun readParts(
        key: String, entry: JsonObject, location: String, diagnostics: MutableList<ImportDiagnostic>
    ): List<ImportPart> {
        val contentType = PostmanValues.optionalText(entry, "contentType", location, diagnostics)
        return when (val type = PostmanValues.text(entry["type"]) ?: "text") {
            "text" -> listOf(
                ImportPart(
                    key,
                    PostmanValues.value(PostmanValues.optionalText(entry, "value", location, diagnostics).orEmpty()),
                    contentType = contentType
                )
            )

            "file" -> fileSources(entry["src"], location, diagnostics).map {
                ImportPart(key, PostmanValues.value(it), true, contentType, PostmanValues.text(entry["filename"]))
            }

            else -> {
                diagnostics += PostmanValues.error(location, "Unsupported multipart field type '$type'.", entry)
                emptyList()
            }
        }
    }

    private fun file(source: JsonElement?, location: String, diagnostics: MutableList<ImportDiagnostic>): ImportBody {
        val entry = source as? JsonObject
        val src = entry?.get("src")
        val path = PostmanValues.text(src)
        if (!path.isNullOrBlank()) return ImportBody.File(PostmanValues.value(path))
        diagnostics += PostmanValues.error(
            location,
            "File body requires a file reference; no upload content was read.",
            source
        )
        return ImportBody.None
    }

    private fun fileSources(
        source: JsonElement?,
        location: String,
        diagnostics: MutableList<ImportDiagnostic>
    ): List<String> {
        val sources = if (source is JsonArray) source.toList() else listOfNotNull(source)
        val paths = sources.mapNotNull { PostmanValues.text(it)?.takeIf(String::isNotBlank) }
        if (paths.isEmpty() || paths.size != sources.size) diagnostics += PostmanValues.error(
            location,
            "Multipart upload requires nonempty file references; no upload content was read.", source
        )
        return paths
    }
}
