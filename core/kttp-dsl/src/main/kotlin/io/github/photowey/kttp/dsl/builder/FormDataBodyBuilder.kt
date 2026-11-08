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
import io.github.photowey.kttp.dsl.FormDataBody

/**
 * Collects ordered form fields for a request body.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
@KttpDSL
class FormDataBodyBuilder {

    private val fields = mutableListOf<Pair<String, String>>()

    fun field(name: String, value: Any) {
        fields.add(name to value.toString())
    }

    internal fun build(): FormDataBody = FormDataBody(fields)
}