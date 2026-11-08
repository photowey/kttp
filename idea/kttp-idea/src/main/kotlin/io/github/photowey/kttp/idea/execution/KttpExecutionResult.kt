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

package io.github.photowey.kttp.idea.execution

/**
 * Request result shared by GUI, CLI parsing and response presentation.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
data class KttpExecutionResult(
    val scriptName: String,
    val success: Boolean,
    val message: String,
    val responseFilePath: String? = null,
    val statusCode: Int? = null,
    val protocol: String? = null,  // HTTP protocol version (e.g., "HTTP/2", "HTTP/1.1")
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val contentType: String? = null,
    val bodyType: String? = null,
    val assertionsPassed: Boolean = true,
    val assertionResults: List<KttpAssertionResultRow> = emptyList(),
    val requestMethod: String? = null,
    val requestUrl: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val requestBody: String? = null,
    val bodyTruncated: Boolean = false,
    val responseSize: Long? = null,
    val executionMode: RequestExecutionMode? = null,
    val totalTimeMs: Long? = null,
    val httpTimeMs: Long? = null
)

/**
 * Carries an assertion's reported path, outcome, and explanatory message.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
data class KttpAssertionResultRow(val path: String, val passed: Boolean, val message: String)
