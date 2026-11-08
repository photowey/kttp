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

package io.github.photowey.kttp.core.context.registry

import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.cast

/**
 * Registers and resolves typed components and closes owned components.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
interface ComponentRegistry :
    ComponentProvider,
    AutoCloseable {
    fun <T : Any> registerComponent(clazz: KClass<T>, instance: T)
}

/**
 * Resolves a component by its Kotlin type.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
interface ComponentProvider {
    fun <T : Any> getComponent(clazz: KClass<T>): T
}

/**
 * Stores typed components and manages their lifecycle.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class ComponentsRegistryImpl : ComponentRegistry {
    val components = mutableMapOf<KClass<*>, Any>()

    override fun <T : Any> registerComponent(clazz: KClass<T>, instance: T) {
        components[clazz] = instance
    }

    override fun <T : Any> getComponent(clazz: KClass<T>): T {
        val component = components[clazz]
        requireNotNull(component) {
            "No component of type $clazz registered."
        }
        return clazz.cast(component)
    }

    /** Attempts to close every registered AutoCloseable component; exceptions are printed and cleanup continues. */
    override fun close() {
        components.values.forEach {
            if (it is AutoCloseable) {
                try {
                    it.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}

inline fun <reified T : Any> ComponentRegistry.registerComponent(instance: T) {
    registerComponent(T::class, instance)
}

inline fun <reified T : Any> ComponentProvider.getComponent(): T {
    return getComponent(T::class)
}

inline operator fun <reified T : Any> ComponentRegistry.provideDelegate(
    thisRef: Any?,
    property: KProperty<*>
): AbstractComponentDelegate<T> {
    return object : AbstractComponentDelegate<T>() {
        override fun getValueImpl(thisRef: Any?, property: KProperty<*>): T {
            return getComponent(T::class)
        }

        override fun setValueImpl(thisRef: Any?, property: KProperty<*>, value: T) {
            registerComponent(value)
        }
    }
}

/**
 * Implements delegated access to components held by a component provider or registry.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
abstract class AbstractComponentDelegate<T> {
    operator fun getValue(
        thisRef: Nothing?,
        property: KProperty<*>
    ): T = getValueImpl(thisRef, property)

    operator fun getValue(
        thisRef: Any?,
        property: KProperty<*>
    ): T = getValueImpl(thisRef, property)

    operator fun setValue(
        thisRef: Nothing?,
        property: KProperty<*>,
        value: T
    ) = setValueImpl(thisRef, property, value)

    operator fun setValue(
        @Suppress("unused") thisRef: Any?,
        @Suppress("unused") property: KProperty<*>,
        value: T
    ) = setValueImpl(thisRef, property, value)

    abstract fun getValueImpl(thisRef: Any?, property: KProperty<*>): T

    abstract fun setValueImpl(thisRef: Any?, property: KProperty<*>, value: T)
}