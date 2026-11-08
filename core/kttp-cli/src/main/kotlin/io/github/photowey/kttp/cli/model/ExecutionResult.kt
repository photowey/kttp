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

package io.github.photowey.kttp.cli.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Execution result for CLI output (JSON format).
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/17
 */
@Serializable
data class CliExecutionResult(
    val success: Boolean,
    val scriptName: String,
    val message: String,
    val statusCode: Int? = null,
    val statusMessage: String? = null,
    val protocol: String? = null,  // HTTP protocol version (e.g., "HTTP/2", "HTTP/1.1")
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val contentType: String? = null,  // e.g., "application/json", "text/html"
    val bodyType: String? = null,     // e.g., "JSON", "XML", "HTML", "TEXT"
    val responseFilePath: String? = null,
    val requestMethod: String? = null,
    val requestUrl: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val requestBody: String? = null,
    val executionTimeMs: Long? = null,
    val error: String? = null,
    val bodyTruncated: Boolean = false,
    val responseSize: Long? = null,
    val httpTimeMs: Long? = null
) {
    fun toJson(): String = Json.encodeToString(serializer(), this)

    /**
     * Decodes serialized CLI execution results from JSON.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/17
     */
    companion object {
        fun fromJson(json: String): CliExecutionResult = Json.decodeFromString(serializer(), json)
    }
}

/**
 * Carries a CLI error message and an optional stack trace.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/17
 */
@Serializable
data class CliExecutionError(
    val message: String,
    val stackTrace: String? = null
)
