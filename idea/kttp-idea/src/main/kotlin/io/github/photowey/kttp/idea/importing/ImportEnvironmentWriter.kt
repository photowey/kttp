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

import kotlinx.serialization.json.*

/**
 * Prepares new named profiles without replacing existing environments or applying a selection.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
object ImportEnvironmentWriter {
    private val json = Json { prettyPrint = true }

    fun merge(existing: String?, bundle: ImportBundle): Pair<String?, List<String>> {
        val inputs = inputs(bundle)
        if (inputs.isEmpty()) return null to emptyList()
        val previous = existing?.let {
            require(Json.parseToJsonElement(it) is JsonObject) { "Existing environment file must be a JSON object" }
            Json.parseToJsonElement(it).jsonObject
        } ?: JsonObject(emptyMap())
        val profiles = previous.toMutableMap()
        var expandedSize = 0L
        val names = inputs.map { environment ->
            val base = profileName(bundle, environment.name)
            var name = base
            var suffix = 2
            while (name in profiles) name = "$base-${suffix++}"
            val resolved = resolvedValues(bundle.variables + environment.values)
            expandedSize += resolved.values.sumOf { it.length.toLong() }
            require(expandedSize <= ImportVariableExpansion.MAX_TOTAL) { "Imported environments exceed the total size limit" }
            profiles[name] = JsonObject(resolved.mapValues { JsonPrimitive(it.value) })
            name
        }
        return json.encodeToString(JsonObject.serializer(), JsonObject(profiles)) + "\n" to names
    }

    fun profileNames(bundle: ImportBundle): List<String> = inputs(bundle).map { profileName(bundle, it.name) }

    private fun inputs(bundle: ImportBundle) = bundle.environments.ifEmpty {
        if (bundle.variables.isEmpty()) emptyList()
        else listOf(ImportedEnvironment(bundle.sourceName?.takeIf(String::isNotBlank) ?: "Imported", emptyMap()))
    }

    private fun profileName(bundle: ImportBundle, name: String): String {
        val prefix = when (bundle.sourceFormat) {
            ImportFormat.POSTMAN -> "postman_"
            ImportFormat.CURL -> "curl_"
            null -> ""
        }
        return prefix + name.trim().ifBlank { "Imported" }
    }

    /**
     * Expands static aliases; unresolved or cyclic entries stay absent so strict execution fails visibly.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun resolvedValues(values: Map<String, String>): Map<String, String> {
        val expansion = ImportVariableExpansion(values, preserveMissing = false)
        return values.keys.mapNotNull { key -> expansion.resolve(key)?.let { key to it } }.toMap()
    }
}
