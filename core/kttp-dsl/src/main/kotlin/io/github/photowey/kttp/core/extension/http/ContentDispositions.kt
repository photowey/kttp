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

package io.github.photowey.kttp.core.extension.http

/**
 * Represents a parsed Content-Disposition type and its parameters.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
data class ContentDisposition(
    val type: String,
    val parameters: Map<String, String>
) {
    /**
     * Parses Content-Disposition header values into their structured representation.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    companion object {
        fun parseFromHeader(header: String): ContentDisposition {
            val parts = header.split(";")
            val type = parts[0].trim()

            val params = parts.drop(1).associate { param ->
                val (key, value) = param.trim().split("=", limit = 2)
                key to value.trim('"')
            }

            return ContentDisposition(type, params)
        }
    }
}

fun ContentDisposition.name() = parameters["name"]
fun ContentDisposition.filename() = parameters["filename"]?.takeIf { it.isNotBlank() }
