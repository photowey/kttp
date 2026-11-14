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
 * One authentication scope, with diagnostics that follow only requests inheriting it.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal data class PostmanAuthentication(
    val value: ImportAuth = ImportAuth.None, val diagnostics: List<ImportDiagnostic> = emptyList()
) {
    companion object {
        fun read(source: JsonElement?, inherited: PostmanAuthentication, location: String): PostmanAuthentication {
            if (source == null || source is JsonNull) return inherited
            val obj = source as? JsonObject ?: return invalid(location, "Authentication must be an object.", source)
            val type = PostmanValues.text(obj["type"])
                ?: return invalid(location, "Authentication requires a type.", source)
            if (type == "noauth") return PostmanAuthentication()
            if (type !in setOf("basic", "bearer", "apikey")) return PostmanAuthentication(
                diagnostics = listOf(
                    PostmanValues.warning(
                        location,
                        "Postman '$type' authentication requires manual conversion.",
                        source
                    )
                )
            )
            val diagnostics = mutableListOf<ImportDiagnostic>()
            val attributes = attributes(obj[type], "$location.$type", diagnostics)
            val auth = when (type) {
                "basic" -> ImportAuth.Basic(
                    PostmanValues.value(attributes["username"].orEmpty()),
                    PostmanValues.value(attributes["password"].orEmpty())
                )

                "bearer" -> ImportAuth.Bearer(PostmanValues.value(required(attributes, "token", location, diagnostics)))
                else -> apiKey(attributes, location, diagnostics)
            }
            return PostmanAuthentication(auth, diagnostics)
        }

        private fun attributes(
            source: JsonElement?,
            location: String,
            diagnostics: MutableList<ImportDiagnostic>
        ): Map<String, String> {
            if (source is JsonObject) return source.mapNotNull { (key, value) ->
                val text = PostmanValues.scalar(value)
                if (text == null) diagnostics += PostmanValues.error(
                    "$location.$key",
                    "Authentication attribute requires a static value.",
                    value
                )
                text?.let { key to it }
            }.toMap()
            if (source !is JsonArray) {
                diagnostics += PostmanValues.error(
                    location,
                    "Authentication attributes must be a v2.0 object or v2.1 array.",
                    source
                )
                return emptyMap()
            }
            val result = linkedMapOf<String, String>()
            source.forEachIndexed { index, item ->
                val entry = item as? JsonObject
                val key = entry?.let { PostmanValues.text(it["key"]) }
                if (entry == null || key == null) {
                    diagnostics += PostmanValues.error(
                        "$location[$index]",
                        "Authentication attribute requires a key.",
                        item
                    )
                } else if (!PostmanValues.disabled(entry)) {
                    val value = PostmanValues.scalar(entry["value"])
                    if (value != null) result[key] = value
                    else diagnostics += PostmanValues.error(
                        "$location[$index]",
                        "Authentication attribute requires a static value.",
                        item
                    )
                }
            }
            return result
        }

        private fun apiKey(
            attributes: Map<String, String>,
            location: String,
            diagnostics: MutableList<ImportDiagnostic>
        ): ImportAuth {
            val key = required(attributes, "key", location, diagnostics)
            PostmanValues.fieldName(key, "$location.apikey.key", "API key", diagnostics)
            val value = required(attributes, "value", location, diagnostics)
            val target = attributes["in"] ?: "header"
            if (target !in setOf("header", "query")) diagnostics += PostmanValues.error(
                location,
                "API key authentication must target a header or query parameter."
            )
            return ImportAuth.ApiKey(key, PostmanValues.value(value), target == "query")
        }

        private fun required(
            attributes: Map<String, String>, key: String, location: String, diagnostics: MutableList<ImportDiagnostic>
        ): String = attributes[key] ?: "".also {
            diagnostics += PostmanValues.error(location, "Authentication requires a '$key' attribute.")
        }

        private fun invalid(location: String, message: String, source: JsonElement): PostmanAuthentication =
            PostmanAuthentication(diagnostics = listOf(PostmanValues.error(location, message, source)))
    }
}
