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

/**
 * Memoizes static aliases and bounds recursion, replacement work and materialized text before allocation grows.
 * Missing references remain placeholders for request-local values, and invalidate executable environment defaults.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/14
 */
internal class ImportVariableExpansion(private val values: Map<String, String>, private val preserveMissing: Boolean) {
    private val cache = mutableMapOf<String, String?>()
    private var references = 0
    private var materialized = 0L

    fun expand(text: String): String? = expand(text, emptySet())
    fun resolve(key: String): String? = resolve(key, emptySet())

    private fun resolve(key: String, seen: Set<String>): String? {
        if (key in seen || seen.size >= 64) return null
        if (cache.containsKey(key)) return cache[key]
        val raw = values[key] ?: return if (preserveMissing) "{{$key}}" else null
        val value = expand(raw, seen + key)
        materialized += value?.length ?: 0
        require(materialized <= MAX_TOTAL) { "Imported variable expansion exceeds the total size limit" }
        cache[key] = value
        return value
    }

    private fun expand(text: String, seen: Set<String>): String? {
        require(text.length <= MAX_VALUE) { "Imported variable value exceeds the size limit" }
        val output = StringBuilder()
        var offset = 0
        for (match in VARIABLE.findAll(text)) {
            require(++references <= 10_000) { "Imported variable expansion exceeds the reference limit" }
            val replacement = resolve(match.groupValues[1], seen) ?: return null
            val literal = text.substring(offset, match.range.first)
            require(output.length.toLong() + literal.length + replacement.length <= MAX_VALUE) {
                "Imported variable value exceeds the size limit"
            }
            output.append(literal).append(replacement)
            offset = match.range.last + 1
        }
        require(output.length.toLong() + text.length - offset <= MAX_VALUE) { "Imported variable value exceeds the size limit" }
        return output.append(text, offset, text.length).toString()
    }

    companion object {
        const val MAX_VALUE = 1_048_576
        const val MAX_TOTAL = 8_388_608
        private val VARIABLE = Regex("\\{\\{([^{}]+)}}")
    }
}
