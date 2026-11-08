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

package io.github.photowey.kttp.core.extension.okhttp

import io.github.photowey.kttp.core.extension.http.ContentDisposition
import okhttp3.Response

/**
 * Asserts that the response HTTP status code equals [expected].
 * Use directly in `thenResponse { requireStatus(200) }`, whose receiver is [Response].
 * In a `then { }` block, use `getResponse().requireStatus(200)` or `assertStatus(200)`.
 * @throws IllegalArgumentException if [Response.code] != [expected]
 */
fun Response.requireStatus(expected: Int) {
    require(code == expected) { "Expected HTTP $expected but got $code" }
}

fun Response.contentDisposition(): ContentDisposition? {
    val textValue = headers["Content-Disposition"] ?: return null
    return ContentDisposition.parseFromHeader(textValue)
}
