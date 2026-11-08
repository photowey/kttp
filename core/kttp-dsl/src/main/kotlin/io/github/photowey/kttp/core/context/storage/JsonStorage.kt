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

package io.github.photowey.kttp.core.context.storage

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.photowey.kttp.core.context.defaultObjectMapper
import java.io.IOException
import java.nio.file.Path
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

/**
 * Persists script values in JSON and merges pending changes when closed.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class JsonStorage(
    private val file: Path,
    val objectMapper: ObjectMapper = defaultObjectMapper,
) : Storage {

    val data: NodeMap = readStorage()

    private val changedData: NodeMap = mutableMapOf()

    /** Converts a stored JSON value to the requested Kotlin type; missing or unconvertible values return null. */
    override fun <T : Any> getValue(key: String, type: KType): T? {
        val jsonNode = data[key] ?: return null
        val javaType = objectMapper.constructType(type.javaType)
        return try {
            objectMapper.convertValue(jsonNode, javaType) as? T
        } catch (e: Exception) {
            println("Unable to parse $key")
            null
        }
    }

    /** Stages a JSON value in memory; persistence is deferred until close. */
    override fun setValue(key: String, value: Any?) {
        val jsonNode = objectMapper.valueToTree<JsonNode>(value)
        data[key] = jsonNode
        changedData[key] = jsonNode
    }

    /**
     * Merges pending keys into the latest readable archive and atomically replaces it.
     * A malformed archive or failed write propagates without clearing the pending changes.
     */
    override fun close() {
        if (changedData.isNotEmpty()) {
            val existingData = readStorage()
            existingData.putAll(changedData)
            AtomicStorageFile.replace(file) { objectMapper.writeValue(it.toFile(), existingData) }
            changedData.clear()
        }
    }

    private fun readStorage(): NodeMap {
        val content = AtomicStorageFile.read(file) ?: return mutableMapOf()
        return try {
            objectMapper.factory.createParser(content).use { parser ->
                if (parser.nextToken() == null) return mutableMapOf()
                objectMapper.readerFor(object : TypeReference<NodeMap>() {})
                    .with(FAIL_ON_TRAILING_TOKENS)
                    .readValue<NodeMap?>(parser)
                    ?: throw IOException("Storage must contain a JSON object")
            }
        } catch (failure: IOException) {
            throw IOException("Cannot parse storage: ${file.toAbsolutePath()}", failure)
        }
    }
}

/**
 * Maps stored variable names to their JSON tree values.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
typealias NodeMap = MutableMap<String, JsonNode>
