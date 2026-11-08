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

package io.github.photowey.kttp.core.context

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.ReadContext
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import okhttp3.Response
import java.util.*

/**
 * Shares JSON parsing and JSONPath configuration within a script context.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class JsonContext() {
    val objectMapper: ObjectMapper = defaultObjectMapper
    private val jsonPaths: WeakHashMap<Response, ReadContext> = WeakHashMap()

    /**
     * Parses and caches JSONPath data per response. The first access consumes its body string.
     * Subsequent JSONPath reads use the cached data; direct body reads still follow OkHttp consumption rules.
     */
    fun getReadContext(response: Response): ReadContext {
        var readContext = jsonPaths[response]

        if (readContext == null) {
            readContext = JsonPath.parse(
                response.body?.string(),
                Configuration.builder()
                    .jsonProvider(JacksonJsonProvider(objectMapper))
                    .mappingProvider(JacksonMappingProvider(objectMapper))
                    .build()
            )

            jsonPaths[response] = readContext

            return readContext
        }

        return readContext
    }
}

val defaultObjectMapper: ObjectMapper by lazy {
    ObjectMapper()
        .registerModules(kotlinModule())
        .enable(JsonGenerator.Feature.IGNORE_UNKNOWN)
        .enable(JsonParser.Feature.IGNORE_UNDEFINED)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
}
