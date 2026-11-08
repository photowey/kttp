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

package io.github.photowey.kttp.core.curl

import okhttp3.Request
import okio.Buffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Renders an OkHttp request as a shell command with a decoded text body.
 * Body serialization is buffered in memory; binary uploads are not exported losslessly.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/18
 */
object CurlConverter {

    /**
     * Renders a multiline shell command containing the method, URL, headers, and a decoded text body.
     * The body is written into an in-memory buffer; this is not a binary-safe file-upload export.
     */
    fun toCurl(request: Request): String {
        val parts = mutableListOf<String>()

        parts.add("curl -X ${request.method}")

        // URL (already includes query string from OkHttp)
        val url = request.url.toString()
        parts.add(shellEscape(url))

        // Headers
        request.headers.forEach { (name, value) ->
            parts.add("-H ${shellEscape("$name: $value")}")
        }

        // Body
        val bodyString = peekRequestBody(request)
        if (!bodyString.isNullOrEmpty()) {
            val escaped = bodyString
                .replace("\\", "\\\\")
                .replace("'", "'\\''")
            parts.add("-d '$escaped'")
        }

        return parts.joinToString(" \\\n  ") { it }
    }

    private fun peekRequestBody(request: Request): String? {
        val body = request.body ?: return null
        val buffer = Buffer()
        body.writeTo(buffer)
        val contentType = body.contentType()
        val charset: Charset = contentType?.charset() ?: StandardCharsets.UTF_8
        return buffer.readByteArray().toString(charset)
    }

    private fun shellEscape(s: String): String {
        return "'" + s.replace("'", "'\\''") + "'"
    }
}
