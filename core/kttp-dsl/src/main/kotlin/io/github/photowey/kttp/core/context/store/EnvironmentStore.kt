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

/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.github.photowey.kttp.core.context.store


import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import kotlin.reflect.KProperty
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf

/**
 * Resolves delegated environment properties and reports whether they are available.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
interface EnvironmentStore {
    operator fun <T> getValue(receiver: Any?, property: KProperty<*>): T

    operator fun contains(property: KProperty<*>): Boolean
}

/**
 * Resolves each environment property from the first store containing it.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class CompoundEnvironmentStore(
    private val stores: List<EnvironmentStore>
) : EnvironmentStore {
    override fun <T> getValue(receiver: Any?, property: KProperty<*>): T {
        return stores.firstOrNull { it.contains(property) }
            ?.getValue<T>(receiver, property)
            ?: throw NoEnvironmentPropertyException(property.name)
    }

    override fun contains(property: KProperty<*>): Boolean {
        return stores.any { it.contains(property) }
    }
}

/**
 * Converts supplied string values into supported delegated property types.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class ValuesEnvironmentStore(
    private val values: Map<String, String>
) : EnvironmentStore {
    @Suppress("UNCHECKED_CAST")
    override fun <T> getValue(receiver: Any?, property: KProperty<*>): T {
        if (property.name !in values) {
            throw NoEnvironmentPropertyException(property.name)
        }

        val value = values[property.name]!!

        return when {
            property.returnType.isSubtypeOf(typeOf<String>()) -> value as T
            property.returnType.isSubtypeOf(typeOf<Int>()) -> value.toInt() as T
            property.returnType.isSubtypeOf(typeOf<Boolean>()) -> value.toBoolean() as T
            property.returnType.isSubtypeOf(typeOf<Double>()) -> value.toDouble() as T
            property.returnType.isSubtypeOf(typeOf<Long>()) -> value.toLong() as T
            else -> throw IllegalArgumentException("Unsupported type for property ${property.name}")
        }
    }

    override fun contains(property: KProperty<*>): Boolean {
        return property.name in values
    }
}

/**
 * Reads delegated environment properties from a named JSON environment in a file.
 * The root and selected environment must be JSON objects. Invalid structure or a missing
 * environment throws [IllegalArgumentException] when the store is first read.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class FileEnvironmentStore(
    private val file: File,
    private val envName: String
) : EnvironmentStore {
    private val values: JsonNode by lazy {
        val objectMapper = ObjectMapper()
        val tree = objectMapper.readTree(file)
        require(tree != null && tree.isObject) {
            "Environment file must contain a JSON object: ${file.path}"
        }
        val envNode = tree.get(envName)
            ?: throw IllegalArgumentException("Environment '$envName' is not defined in ${file.path}")
        require(envNode.isObject) {
            "Environment '$envName' must be a JSON object in ${file.path}"
        }
        envNode
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getValue(receiver: Any?, property: KProperty<*>): T {
        val node = values.get(property.name)
            ?: throw NoEnvironmentPropertyException(property.name)

        return when {
            property.returnType.isSubtypeOf(typeOf<String>()) -> node.asText() as T
            property.returnType.isSubtypeOf(typeOf<Int>()) -> node.asInt() as T
            property.returnType.isSubtypeOf(typeOf<Boolean>()) -> node.asBoolean() as T
            property.returnType.isSubtypeOf(typeOf<Double>()) -> node.asDouble() as T
            property.returnType.isSubtypeOf(typeOf<Long>()) -> node.asLong() as T
            else -> throw IllegalArgumentException("Unsupported type for property ${property.name}")
        }
    }

    override fun contains(property: KProperty<*>): Boolean {
        return values.get(property.name) != null
    }
}

/**
 * Represents an environment store containing no properties.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
object NoopEnvironmentStore : EnvironmentStore {
    override fun <T> getValue(receiver: Any?, property: KProperty<*>): T =
        throw NoEnvironmentPropertyException(property.name)

    override fun contains(property: KProperty<*>): Boolean = false
}

/**
 * Wraps an [EnvironmentStore] and returns default values for missing keys so script registration
 * can complete (e.g. all GET/POST blocks run). Missing String -> "", missing Int -> 0, etc.
 * Records which keys were read as unset so execution can throw before using the request.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class LenientEnvironmentStore(
    private val delegate: EnvironmentStore
) : EnvironmentStore {

    private val unsetKeysRead = mutableSetOf<String>()

    @Suppress("UNCHECKED_CAST")
    override fun <T> getValue(receiver: Any?, property: KProperty<*>): T {
        if (delegate.contains(property)) {
            return delegate.getValue(receiver, property)
        }
        unsetKeysRead.add(property.name)
        return when {
            property.returnType.isSubtypeOf(typeOf<String>()) -> "" as T
            property.returnType.isSubtypeOf(typeOf<Int>()) -> 0 as T
            property.returnType.isSubtypeOf(typeOf<Long>()) -> 0L as T
            property.returnType.isSubtypeOf(typeOf<Boolean>()) -> false as T
            property.returnType.isSubtypeOf(typeOf<Double>()) -> 0.0 as T
            else -> "" as T
        }
    }

    override fun contains(property: KProperty<*>): Boolean = true

    /**
     * Returns and clears the set of env keys that were read since last call and were unset.
     * Call once per request registration so each registration gets the keys used for that request.
     */
    fun takeUnsetKeysAndClear(): Set<String> = unsetKeysRead.toSet().also { unsetKeysRead.clear() }
}

/**
 * Reports that no configured store contains a requested environment property.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class NoEnvironmentPropertyException(prop: String) :
    Exception("Unable to find property '$prop'")

/**
 * Resolves delegated properties from the supplied string values without defaults.
 * A missing key in an empty map throws [NoEnvironmentSelectedException]; a missing key in
 * a nonempty map throws [EnvironmentVariableNotFoundException]. Values are converted on access.
 * Callers supply values from the selected IDE environment or CLI --env-file / --env-param options.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class StrictEnvironmentStore(
    private val values: Map<String, String>
) : EnvironmentStore {

    @Suppress("UNCHECKED_CAST")
    override fun <T> getValue(receiver: Any?, property: KProperty<*>): T {
        val name = property.name
        if (name !in values) {
            if (values.isEmpty()) {
                throw NoEnvironmentSelectedException(
                    "未选择环境。脚本中使用了环境变量 '$name'，但当前未选择环境 (dev/test)。" +
                        "请在 Run Configuration 中选择环境，或使用 --env-file / --env 指定变量。"
                )
            }
            throw EnvironmentVariableNotFoundException(
                name,
                "环境变量 '$name' 在当前环境中未定义。请检查 kttp.env.json 或 --env 参数。"
            )
        }
        val value = values[name]!!
        return when {
            property.returnType.isSubtypeOf(typeOf<String>()) -> value as T
            property.returnType.isSubtypeOf(typeOf<Int>()) -> value.toInt() as T
            property.returnType.isSubtypeOf(typeOf<Boolean>()) -> value.toBoolean() as T
            property.returnType.isSubtypeOf(typeOf<Double>()) -> value.toDouble() as T
            property.returnType.isSubtypeOf(typeOf<Long>()) -> value.toLong() as T
            else -> throw IllegalArgumentException("Unsupported type for property $name")
        }
    }

    override fun contains(property: KProperty<*>): Boolean =
        property.name in values
}

/**
 * Reports an environment read attempted without a selected environment.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class NoEnvironmentSelectedException(message: String) : Exception(message)

/**
 * Reports a missing variable in the selected environment.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class EnvironmentVariableNotFoundException(
    val variableName: String,
    message: String
) : Exception(message)
