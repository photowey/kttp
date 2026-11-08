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

package io.github.photowey.kttp.dsl.extension

import io.github.photowey.kttp.core.annotation.HeaderValue
import io.github.photowey.kttp.dsl.builder.RequestBuilder
import java.util.*

fun RequestBuilder.basicAuth(username: String, password: String) {
    val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
    header("Authorization", "Basic $token")
}

fun RequestBuilder.bearerAuth(token: String) {
    header("Authorization", "Bearer $token")
}

fun RequestBuilder.contentType(@HeaderValue("Content-Type") contentType: String) {
    header("Content-Type", contentType)
}

fun RequestBuilder.accept(@HeaderValue("Accept") contentType: String) {
    header("Accept", contentType)
}
