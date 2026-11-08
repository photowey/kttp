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

import io.github.photowey.kttp.core.context.ClientConfigurer
import io.github.photowey.kttp.core.context.KttpContext
import io.github.photowey.kttp.core.context.store.LenientEnvironmentStore
import io.github.photowey.kttp.core.context.store.StoredVariableDelegate
import io.github.photowey.kttp.core.context.execution.DeclarationCoordinates
import io.github.photowey.kttp.core.context.execution.Executable
import io.github.photowey.kttp.core.context.execution.result.ResultExecutable
import io.github.photowey.kttp.core.context.execution.result.RequestBuilderProvider
import io.github.photowey.kttp.core.context.execution.result.RequestHolder
import io.github.photowey.kttp.core.context.execution.result.onResultObtained
import io.github.photowey.kttp.core.extension.auth.AuthExtensions
import io.github.photowey.kttp.core.extension.jsonpath.JsonPathExtension
import io.github.photowey.kttp.dsl.StoredValueDelegate
import io.github.photowey.kttp.dsl.ValueDelegate
import kotlin.reflect.KProperty

/**
 * Registers context-backed request holders and connects delegated results to script storage.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
internal class KttpBuilderImpl(private val context: KttpContext) :
    KttpBuilder,
    JsonPathExtension by context.jsonpath,
    AuthExtensions by context.auth {

    override val env = context.environmentStore
    override val vars = context.variablesStore
    override fun variable(): StoredVariableDelegate = vars.variable()

    /** Replaces the context-wide client configuration applied before each request-specific configuration. */
    override fun configureClient(configure: ClientConfigurer) {
        context.clientContext.globalConfigurer = configure
    }

    override fun request(
        method: String,
        path: String,
        name: String?,
        configure: RequestBuilder.() -> Unit
    ): RequestHolder {
        val envKeysUsedUnset = when (val store = context.environmentStore) {
            is LenientEnvironmentStore -> store.takeUnsetKeysAndClear()
            else -> emptySet()
        }
        val requestBuilderProvider = RequestBuilderProvider {
            RequestBuilder(method, path, context).apply(configure)
        }
        val requestHolder = RequestHolder(requestBuilderProvider, context)
        context.executionContext.registerExecutable(requestHolder, name, envKeysUsedUnset)
        return requestHolder
    }

    /**
     * Adds the property name as a coordinate and stores observed results under that name.
     * The returned delegate executes the holder when no stored value is available.
     */
    override operator fun <R> ResultExecutable<R>.provideDelegate(
        @Suppress("unused")
        receiver: Any?,
        prop: KProperty<*>
    ): ValueDelegate<R> {
        val executable = this
        // Register this declaration with delegating property name
        registerExecutableWithPropName(prop, executable.originalExecutable)
        val storedValue = UpdatableStoredValue(prop, executable)
        return StoredValueDelegate(
            context,
            executable,
            storedValue::value
        )
    }

    private fun registerExecutableWithPropName(prop: KProperty<*>, executable: Executable<*>) {
        val coordinates = DeclarationCoordinates(prop.name)
        context.executionContext.addCoordinatesForExecutable(coordinates, executable)
    }

    /**
     * Stores an executable result under the declaring property's name.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    inner class UpdatableStoredValue<R>(
        private val prop: KProperty<*>,
        requestHolder: ResultExecutable<R>
    ) {
        private val key = prop.name
        private val storage = context.variablesStore

        var value: R?
            get() = storage.getValue(key, prop.returnType)
            set(value) {
                storage.setValue(key, value)
            }

        init {
            requestHolder.onResultObtained<R> {
                value = it
            }
        }
    }
}