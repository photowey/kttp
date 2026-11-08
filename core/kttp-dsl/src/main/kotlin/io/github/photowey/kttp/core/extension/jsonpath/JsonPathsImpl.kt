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

package io.github.photowey.kttp.core.extension.jsonpath

import com.fasterxml.jackson.databind.JsonNode
import com.jayway.jsonpath.ReadContext
import com.jayway.jsonpath.TypeRef
import io.github.photowey.kttp.core.context.KttpContext
import okhttp3.Response

/**
 * Evaluates JSONPath expressions using the context's JSON services.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class JsonPathsImpl(private val context: KttpContext) : JsonPathExtension {
    override fun Response.jsonPath(): ReadContext {
        return context.jsonContext.getReadContext(this)
    }

    override fun <T> ReadContext.readList(path: String, clazz: Class<T>): List<T> {
        val nodes: List<JsonNode> = read(path, object : TypeRef<List<JsonNode>>() {})
        return nodes.map {
            context.jsonContext.objectMapper.treeToValue(it, clazz)
        }
    }
}