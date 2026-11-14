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

import org.junit.Test
import kotlin.test.assertEquals

/**
 * Pins the common request contract of both import strategies.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
class ImportParserContractTest {
    @Test
    fun curlProducesOneGet() {
        val bundle = CurlImportParser().parse(ImportInput("curl 'https://example.com/get'"))
        assertEquals("GET", bundle.requests.single().method)
        assertEquals("https://example.com/get", bundle.requests.single().url.text)
    }

    @Test
    fun postmanProducesOneGetWithoutEnvironment() {
        val text =
            """{"info":{"name":"Demo","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},"item":[{"name":"Read","request":{"method":"GET","url":"https://example.com/get"}}]}"""
        val bundle = PostmanImportParser().parse(ImportInput(text))
        assertEquals("GET", bundle.requests.single().method)
        assertEquals("Read", bundle.requests.single().name)
    }
}
