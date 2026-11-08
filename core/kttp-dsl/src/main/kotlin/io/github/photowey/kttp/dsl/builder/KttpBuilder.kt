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
import io.github.photowey.kttp.core.context.ClientConfigurer
import io.github.photowey.kttp.core.context.store.EnvironmentStore
import io.github.photowey.kttp.core.context.store.StoredVariableDelegate
import io.github.photowey.kttp.core.context.store.VariablesStore
import io.github.photowey.kttp.core.context.execution.result.ResultExecutable
import io.github.photowey.kttp.core.context.execution.result.RequestHolder
import io.github.photowey.kttp.core.extension.auth.AuthExtensions
import io.github.photowey.kttp.core.extension.jsonpath.JsonPathExtension
import io.github.photowey.kttp.dsl.RequestRegistrator
import io.github.photowey.kttp.dsl.ValueDelegate
import kotlin.reflect.KProperty

/**
 * Combines request registration, environment values, stored variables, and DSL extensions.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
@KttpDSL
interface KttpBuilder :
    RequestRegistrator<RequestHolder>,
    JsonPathExtension,
    AuthExtensions {

    val env: EnvironmentStore
    val vars: VariablesStore

    fun variable(): StoredVariableDelegate

    fun configureClient(configure: ClientConfigurer)

    operator fun <R> ResultExecutable<R>.provideDelegate(
        @Suppress("unused")
        receiver: Any?,
        prop: KProperty<*>
    ): ValueDelegate<R>
}
