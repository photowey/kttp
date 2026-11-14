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
import kotlinx.serialization.json.booleanOrNull

/**
 * Shared value conversion and diagnostics for the pure Postman import readers.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal object PostmanValues {
    private val reference = Regex("\\{\\{([^{}]+)}}")

    fun text(element: JsonElement?): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.content

    fun scalar(element: JsonElement?): String? = (element as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content

    fun optionalText(
        entry: JsonObject, key: String, location: String, diagnostics: MutableList<ImportDiagnostic>
    ): String? {
        val text = text(entry[key])
        if (entry.containsKey(key) && text == null) diagnostics += error(
            "$location.$key",
            "Expected a string value.",
            entry[key]
        )
        return text
    }

    fun bool(element: JsonElement?): Boolean? = (element as? JsonPrimitive)?.booleanOrNull

    fun value(text: String): ImportValue = ImportValue(text, reference.containsMatchIn(text))

    fun fieldName(
        name: String?, location: String, kind: String, diagnostics: MutableList<ImportDiagnostic>
    ) {
        if (name != null && reference.containsMatchIn(name)) diagnostics += error(
            location,
            "Postman variables in $kind names are unsupported; convert the name before importing.",
            JsonPrimitive(name)
        )
    }

    fun error(location: String, message: String, source: JsonElement? = null): ImportDiagnostic =
        ImportDiagnostic(ImportSeverity.ERROR, location, message, retainedSource = source?.toString())

    fun warning(location: String, message: String, source: JsonElement? = null): ImportDiagnostic =
        ImportDiagnostic(ImportSeverity.WARNING, location, message, true, source?.toString())

    fun disabled(obj: JsonObject): Boolean = bool(obj["disabled"]) == true || bool(obj["enabled"]) == false

    fun variables(
        source: JsonElement?,
        location: String,
        diagnostics: MutableList<ImportDiagnostic>
    ): Map<String, String> {
        if (source == null || source is JsonNull) return emptyMap()
        if (source !is JsonArray) {
            diagnostics += error(location, "Variables must be an array.", source)
            return emptyMap()
        }
        val values = linkedMapOf<String, String>()
        source.forEachIndexed { index, item ->
            val entry = item as? JsonObject
            if (entry == null) diagnostics += error("$location[$index]", "Variable must be an object.", item)
            else if (!disabled(entry)) readVariable(entry, "$location[$index]", values, diagnostics)
        }
        return values
    }

    private fun readVariable(
        entry: JsonObject,
        location: String,
        values: MutableMap<String, String>,
        diagnostics: MutableList<ImportDiagnostic>
    ) {
        val key = text(entry["key"]) ?: text(entry["id"])
        if (key.isNullOrBlank()) {
            diagnostics += error(location, "Enabled variable requires a nonempty key.", entry)
            return
        }
        val source = entry["value"]
        if (source == null || source is JsonNull) {
            diagnostics += ImportDiagnostic(
                ImportSeverity.WARNING,
                location,
                "Variable '$key' has no static value; its reference remains unresolved."
            )
        } else values[key] = text(source) ?: source.toString()
    }

    fun events(source: JsonElement?, location: String): List<ImportDiagnostic> {
        if (source == null || source is JsonNull) return emptyList()
        if (source !is JsonArray) return listOf(error(location, "Events must be an array.", source))
        return source.mapIndexedNotNull { index, item ->
            val event = item as? JsonObject
            when {
                event == null -> error("$location[$index]", "Event must be an object.", item)
                disabled(event) -> null
                else -> warning(
                    "$location[$index]",
                    "Postman ${text(event["listen"]) ?: "event"} script is retained for review and will not run.",
                    event
                )
            }
        }
    }

    fun references(values: List<ImportValue>, known: Map<String, String>, location: String): List<ImportDiagnostic> {
        val diagnostics = mutableListOf<ImportDiagnostic>()
        val seen = mutableSetOf<String>()
        val pending =
            values.flatMap { reference.findAll(it.text).map { match -> match.groupValues[1] }.toList() }.toMutableList()
        var index = 0
        while (index < pending.size) {
            val name = pending[index++]
            if (seen.add(name)) {
                inspectReference(name, known, location, diagnostics)
                known[name]?.let { value -> pending += reference.findAll(value).map { it.groupValues[1] }.toList() }
            }
        }
        return diagnostics
    }

    private fun inspectReference(
        name: String, known: Map<String, String>, location: String, diagnostics: MutableList<ImportDiagnostic>
    ) {
        when {
            name.startsWith('$') -> diagnostics += ImportDiagnostic(
                ImportSeverity.WARNING, location,
                "Dynamic Postman variable '$name' requires manual conversion.", true, "{{$name}}"
            )

            name !in known -> diagnostics += ImportDiagnostic(
                ImportSeverity.WARNING, location,
                "Variable '$name' is undefined; its reference is preserved."
            )
        }
    }

    fun environmentDiagnostics(
        defaults: Map<String, String>,
        environments: List<ImportedEnvironment>
    ): List<ImportDiagnostic> {
        val profiles = environments.ifEmpty { listOf(ImportedEnvironment("Collection defaults", emptyMap())) }
        return profiles.flatMap { environment ->
            val known = defaults + environment.values
            known.flatMap { (key, value) ->
                val location = "${environment.name}.values.$key"
                val issues = references(listOf(PostmanValues.value(value)), known, location).map {
                    it.copy(
                        message = "Environment value '$key': ${it.message} Unresolved values will not be imported as executable defaults.",
                        retainedSource = it.retainedSource ?: value
                    )
                }.toMutableList()
                if (hasReferenceCycle(key, known)) issues += ImportDiagnostic(
                    ImportSeverity.WARNING,
                    location,
                    "Environment value '$key' has cyclic references and will not be imported as an executable default.",
                    retainedSource = value
                )
                issues
            }
        }
    }

    private fun hasReferenceCycle(start: String, known: Map<String, String>): Boolean {
        val active = mutableSetOf<String>()
        val completed = mutableSetOf<String>()
        val pending = ArrayDeque<Pair<String, Boolean>>()
        pending.addLast(start to false)
        while (pending.isNotEmpty()) {
            val (name, leaving) = pending.removeLast()
            if (leaving) {
                active.remove(name)
                completed += name
            } else {
                if (name in active) return true
                if (name in completed) continue
                active += name
                pending.addLast(name to true)
                reference.findAll(known[name].orEmpty()).forEach { pending.addLast(it.groupValues[1] to false) }
            }
        }
        return false
    }

    fun requestValues(request: ImportedRequest): List<ImportValue> = buildList {
        add(request.url)
        addAll(request.headers.flatMap { listOf(value(it.name), it.value) })
        addAll(request.query.flatMap { listOf(value(it.name), it.value) })
        when (val auth = request.auth) {
            is ImportAuth.Basic -> addAll(listOf(auth.username, auth.password))
            is ImportAuth.Bearer -> add(auth.token)
            is ImportAuth.ApiKey -> addAll(listOf(value(auth.key), auth.value))
            ImportAuth.None -> Unit
        }
        when (val body = request.body) {
            is ImportBody.Text -> add(body.content)
            is ImportBody.Form -> addAll(body.fields.flatMap { listOf(value(it.name), it.value) })
            is ImportBody.Multipart -> addAll(body.parts.flatMap { listOf(value(it.name), it.value) })
            is ImportBody.File -> add(body.path)
            ImportBody.None -> Unit
        }
        addAll(request.variables.values.map(::value))
    }
}
