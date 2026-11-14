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

/**
 * Reads enabled headers and flags request options needing manual review.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal object PostmanRequestOptions {
    fun headers(source: JsonElement?, location: String, diagnostics: MutableList<ImportDiagnostic>): List<ImportField> {
        if (source == null || source is JsonNull) return emptyList()
        if (source is JsonPrimitive && source.isString) return source.content.lineSequence().filter(String::isNotBlank)
            .mapIndexedNotNull { index, line ->
                val colon = line.indexOf(':')
                if (colon <= 0) {
                    diagnostics += PostmanValues.error("$location[$index]", "Header line requires a name and colon.")
                    null
                } else header(
                    line.substring(0, colon).trim(),
                    line.substring(colon + 1).trimStart(),
                    "$location[$index]",
                    diagnostics
                )
            }.toList()
        if (source !is JsonArray) {
            diagnostics += PostmanValues.error(location, "Headers must be an array or text.", source)
            return emptyList()
        }
        return source.mapIndexedNotNull { index, item ->
            val entry = item as? JsonObject
            if (entry != null && PostmanValues.disabled(entry)) null
            else {
                val key = entry?.let { PostmanValues.text(it["key"]) }
                val value = entry?.let { PostmanValues.text(it["value"]) }
                if (key == null || value == null) {
                    diagnostics += PostmanValues.error(
                        "$location[$index]",
                        "Enabled header requires a key and value.",
                        item
                    )
                    null
                } else header(key, value, "$location[$index]", diagnostics)
            }
        }
    }

    private fun header(
        name: String,
        value: String,
        location: String,
        diagnostics: MutableList<ImportDiagnostic>
    ): ImportField? {
        PostmanValues.fieldName(name, "$location.key", "header", diagnostics)
        if (name.isEmpty() || name.any { it <= ' ' || it == ':' } || value.any { it == '\r' || it == '\n' }) {
            diagnostics += PostmanValues.error(location, "Header name or value contains unsupported characters.")
            return null
        }
        return ImportField(name, PostmanValues.value(value))
    }

    fun profile(
        source: JsonElement?, inheritedNoRedirect: Boolean, location: String, diagnostics: MutableList<ImportDiagnostic>
    ): Boolean {
        if (source == null || source is JsonNull) return inheritedNoRedirect
        val profile = source as? JsonObject
        if (profile == null) {
            diagnostics += PostmanValues.error(location, "Protocol profile behavior must be an object.", source)
            return inheritedNoRedirect
        }
        profile.filterKeys { it != "followRedirects" }.forEach { (key, value) ->
            diagnostics += PostmanValues.warning(
                "$location.$key",
                "Postman protocol option '$key' requires manual review.",
                value
            )
        }
        val redirects = PostmanValues.bool(profile["followRedirects"])
        if ("followRedirects" in profile && redirects == null) diagnostics += PostmanValues.error(
            location,
            "followRedirects must be a boolean.", profile["followRedirects"]
        )
        return redirects?.not() ?: inheritedNoRedirect
    }

    fun unsupported(request: JsonObject, location: String): List<ImportDiagnostic> =
        request.filterKeys {
            it !in setOf(
                "url",
                "method",
                "auth",
                "header",
                "body",
                "description",
                "variable",
                "event",
                "protocolProfileBehavior"
            )
        }
            .map { (key, value) ->
                PostmanValues.warning(
                    "$location.$key",
                    "Postman request option '$key' requires manual review.",
                    value
                )
            }
}
