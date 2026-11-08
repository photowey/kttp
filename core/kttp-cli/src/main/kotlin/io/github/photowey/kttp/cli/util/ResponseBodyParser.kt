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

package io.github.photowey.kttp.cli.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import okhttp3.Response

/**
 * Classifies response text by Content-Type and pretty-prints valid JSON.
 * XML, HTML, plain text, and unrecognized content are returned without formatting.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/17
 */
object ResponseBodyParser {

    private val objectMapper = ObjectMapper().apply {
        enable(SerializationFeature.INDENT_OUTPUT)
    }

    /**
     * Parses response body based on Content-Type header.
     * @param response The OkHttp response
     * @param bodyString The raw body string
     * @return Body text and its detected type; null and empty text are classified as EMPTY.
     */
    fun parse(response: Response, bodyString: String?): ParsedBody {
        if (bodyString.isNullOrEmpty()) {
            return ParsedBody(bodyString, BodyType.EMPTY)
        }

        val contentType = response.headers["Content-Type"] ?: ""

        return when {
            contentType.contains("application/json", ignoreCase = true) -> {
                parseJson(bodyString)
            }

            contentType.contains("application/xml", ignoreCase = true) ||
                contentType.contains("text/xml", ignoreCase = true) -> {
                parseXml(bodyString)
            }

            contentType.contains("text/html", ignoreCase = true) -> {
                ParsedBody(bodyString, BodyType.HTML)
            }

            contentType.contains("text/plain", ignoreCase = true) -> {
                ParsedBody(bodyString, BodyType.TEXT)
            }

            else -> ParsedBody(bodyString, BodyType.UNKNOWN)
        }
    }

    private fun parseJson(bodyString: String): ParsedBody {
        return try {
            val jsonNode = objectMapper.readTree(bodyString)
            val formatted = objectMapper.writeValueAsString(jsonNode)
            ParsedBody(formatted, BodyType.JSON)
        } catch (e: Exception) {
            // If parsing fails, return original
            ParsedBody(bodyString, BodyType.JSON)
        }
    }

    private fun parseXml(bodyString: String): ParsedBody {
        // For now, just return as-is. XML pretty-printing can be added later.
        return ParsedBody(bodyString, BodyType.XML)
    }

    /**
     * Pairs response text with its detected content category.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/17
     */
    data class ParsedBody(
        val content: String?,
        val type: BodyType
    )

    /**
     * Identifies the content categories used when presenting response bodies.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/17
     */
    enum class BodyType {
        JSON,
        XML,
        HTML,
        TEXT,
        UNKNOWN,
        EMPTY
    }
}
