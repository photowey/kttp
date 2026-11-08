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

package io.github.photowey.kttp.idea.run

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.photowey.kttp.core.util.UserAgentProvider
import io.github.photowey.kttp.idea.execution.KttpExecutionResult

/**
 * Formats [KttpExecutionResult] for the Run console.
 * Structure: request line + request headers + request body, blank, response status + response headers + response body, footer.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
object KttpTerminalOutputFormatter {

    private val objectMapper = jacksonObjectMapper()

    fun format(result: KttpExecutionResult): String {
        if (!result.success) {
            return "Execution failed: ${result.message}\n"
        }
        val sb = StringBuilder()

        // Request
        val method = result.requestMethod ?: "GET"
        val url = result.requestUrl ?: ""
        sb.append("$method $url\n")
        val reqHeaders = result.requestHeaders.toMutableMap()
        if (!reqHeaders.containsKey("User-Agent")) {
            reqHeaders["User-Agent"] = UserAgentProvider.userAgent()
        }
        // Format header names (capitalize first letter of each word)
        reqHeaders.forEach { (k, v) ->
            val formattedKey = formatHeaderName(k)
            sb.append("$formattedKey: $v\n")
        }
        sb.append("\n")

        // Request body (format JSON if applicable)
        result.requestBody?.let { body ->
            val formattedBody = formatJsonIfApplicable(body, result.requestHeaders["Content-Type"])
            sb.append(formattedBody).append("\n\n")
        } ?: sb.append("\n")

        // Response
        val statusCode = result.statusCode ?: 0
        // Use actual protocol from result, fallback to HTTP/2 if not available
        val protocol = result.protocol ?: "HTTP/2"
        // Print protocol and status code without the status message.
        sb.append("$protocol $statusCode\n")
        // Response headers (lowercase names)
        result.headers.forEach { (k, v) ->
            val formattedKey = k.lowercase()
            sb.append("$formattedKey: $v\n")
        }
        sb.append("\n")

        // Response body (format JSON if applicable)
        result.body?.let { body ->
            val formattedBody = formatJsonIfApplicable(body, result.contentType)
            sb.append(formattedBody).append("\n\n")
        } ?: sb.append("\n")

        // Footer
        sb.append("Response file saved.\n")
        result.responseFilePath?.let { sb.append("> $it\n") }
        sb.append("\n")

        return sb.toString()
    }

    /**
     * Format header name (capitalize first letter of each word).
     * Examples: "content-type" -> "Content-Type", "user-agent" -> "User-Agent"
     */
    private fun formatHeaderName(name: String): String {
        return name.split("-")
            .joinToString("-") { word ->
                word.lowercase().replaceFirstChar { it.uppercaseChar() }
            }
    }

    /**
     * Pretty-print bodies with a JSON content type. Non-JSON or invalid JSON bodies
     * have surrounding whitespace trimmed.
     */
    private fun formatJsonIfApplicable(body: String, contentType: String?): String {
        if (contentType?.contains("json", ignoreCase = true) == true) {
            return try {
                val node = objectMapper.readTree(body)
                objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(node)
            } catch (_: Exception) {
                body.trim()
            }
        }
        return body.trim()
    }
}
