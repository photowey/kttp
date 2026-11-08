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

package io.github.photowey.kttp.core.context.store

import io.github.photowey.kttp.core.context.storage.Storage
import kotlin.reflect.KProperty
import kotlin.reflect.KType

/**
 * Provides typed helpers and delegates for named script values in storage.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class VariablesStore(val values: Storage) {
    fun string() = DelegateProvider<String>(values)
    fun int() = DelegateProvider<Int>(values)
    fun <T> obj() = DelegateProvider<T>(values)

    fun variable() = StoredVariableDelegate(values)

    fun <T> setValue(name: String, value: T?) = values.setValue(name, value)
    fun <T> getValue(name: String, type: KType): T? = values.getValue(name, type)
}

/**
 * Creates a typed variable delegate using the declaring property's name.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class DelegateProvider<T>(private val values: Storage) {
    private var variable: Variable<T>? = null

    operator fun getValue(receiver: Any?, property: KProperty<*>): Variable<T> {
        if (variable != null) {
            return variable!!
        }
        variable = Variable<T>(values, property)

        return variable!!
    }
}

/**
 * Reads and writes one named typed value in script storage.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class Variable<T>(
    private val storage: Storage,
    private val property: KProperty<*>
) {
    fun getValue(): T? {
        return storage.getValue(property.name, property.getter.returnType) as T?
    }

    fun setValue(value: T) {
        storage.setValue(property.name, value)
    }

    override fun toString(): String = this.getValue().toString()
}

/**
 * Connects delegated property access to the underlying script value storage.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class StoredVariableDelegate(val storage: Storage) {
    inline fun <reified T> getValue(
        thisRef: Any,
        property: KProperty<*>
    ): T {
        return storage.getValue(property.name, property.getter.returnType)!!
    }

    inline fun <reified T> getValue(
        thisRef: Nothing?,
        property: KProperty<*>
    ): T {
        return storage.getValue(property.name, property.getter.returnType)!!
    }

    inline operator fun <reified T> setValue(
        thisRef: Any,
        property: KProperty<*>,
        value: T
    ) {
        storage.setValue(property.name, value)
    }

    inline operator fun <reified T> setValue(
        thisRef: Nothing?,
        property: KProperty<*>,
        value: T
    ) {
        storage.setValue(property.name, value)
    }
}
