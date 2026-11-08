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

package io.github.photowey.kttp.dsl.builder

import io.github.photowey.kttp.core.annotation.KttpDSL
import io.github.photowey.kttp.dsl.ByteArrayBody
import io.github.photowey.kttp.dsl.MultipartBody
import io.github.photowey.kttp.dsl.RequestBody
import io.github.photowey.kttp.dsl.StringBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.File

/**
 * Collects file and content parts for a multipart request body.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
@KttpDSL
class MultipartBodyBuilder(private val boundary: String) {
    private val parts = mutableListOf<MultipartBody.Part>()

    /** Appends a multipart section, optionally supplying its form name and content type. */
    fun part(name: String? = null, contentType: String? = null, block: PartBuilder.() -> Unit) {
        parts.add(PartBuilder(contentType).apply {
            if (name != null) {
                contentDisposition(args = listOf("name" to name))
            }
        }.apply(block).build())
    }

    /** Reads the entire file now and appends a named byte-array part with the supplied filename. */
    fun file(name: String, fileName: String, file: File) {
        part {
            contentDisposition(args = listOf("name" to name, "filename" to fileName))
            body(file.readBytes())
        }
    }

    internal fun build(): MultipartBody = MultipartBody(parts, boundary)

    /**
     * Builds a multipart section's body and headers.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    @KttpDSL
    class PartBuilder(private val contentType: String? = null) {
        private var body: RequestBody? = null
            set(value) {
                require(field == null) { "Body already set" }
                field = value
            }
        private val headers = mutableListOf<Pair<String, Any>>()

        fun body(body: String) {
            this.body = StringBody(body)
        }

        fun body(body: ByteArray) {
            this.body = ByteArrayBody(body)
        }

        fun formData(block: FormDataBodyBuilder.() -> Unit) {
            this.body = FormDataBodyBuilder().apply(block).build()
        }

        fun header(name: String, value: Any) {
            headers.add(name to value)
        }

        fun contentDisposition(value: String = "form-data", args: List<Pair<String, String>>) {
            headers.removeIf { it.first == "Content-Disposition" }
            header(
                "Content-Disposition",
                "$value; " + args.joinToString(separator = ";") { it.first + "=" + "\"" + it.second + "\"" })
        }

        /** Builds a section, rejecting a missing body and validating an explicitly supplied media type. */
        internal fun build(): MultipartBody.Part {
            val body = body
            require(body != null) { "Body is mandatory" }
            return MultipartBody.Part(body, headers, contentType?.toMediaType())
        }
    }
}