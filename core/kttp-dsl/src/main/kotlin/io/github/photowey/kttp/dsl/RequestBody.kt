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

package io.github.photowey.kttp.dsl

import okhttp3.MediaType

/**
 * Identifies the internal body representations accepted by the request builder.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
internal sealed interface RequestBody

/**
 * Carries an HTTP body as raw bytes.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
internal class ByteArrayBody(val body: ByteArray) : RequestBody

/**
 * Carries ordered fields for an URL-encoded form body.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
internal class FormDataBody(val body: List<Pair<String, String>>) : RequestBody

/**
 * Carries multipart sections together with their boundary.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
internal class MultipartBody(val parts: List<Part>, val boundary: String) : RequestBody {
    /**
     * Associates one multipart section's body with its headers.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    class Part(val body: RequestBody, val headers: List<Pair<String, Any>> = listOf(), val contentType: MediaType?)
}

/**
 * Carries an HTTP body as text.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
internal class StringBody(val body: String) : RequestBody
