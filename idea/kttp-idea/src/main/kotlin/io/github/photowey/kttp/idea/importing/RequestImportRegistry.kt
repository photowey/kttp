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

package io.github.photowey.kttp.idea.importing

/**
 * Lists import formats explicitly; adding a parser does not change the dialog or destination logic.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
enum class ImportFormat(private val title: String) {
    CURL("cURL"), POSTMAN("Postman");

    override fun toString(): String = title
}

/**
 * Central strategy registry for the supported import formats.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
object RequestImportRegistry {
    private val parsers = mapOf(ImportFormat.CURL to CurlImportParser(), ImportFormat.POSTMAN to PostmanImportParser())
    fun parser(format: ImportFormat): RequestImportParser = parsers.getValue(format)
}
