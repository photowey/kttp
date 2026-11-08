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

import io.github.photowey.kttp.core.context.execution.ExecutionContext
import io.github.photowey.kttp.core.context.factory.KttpBuilderFactory
import io.github.photowey.kttp.core.context.factory.KttpBuilderFactoryImpl
import io.github.photowey.kttp.core.context.registry.ComponentProvider
import io.github.photowey.kttp.core.context.registry.ComponentRegistry
import io.github.photowey.kttp.core.context.registry.ComponentsRegistryImpl
import io.github.photowey.kttp.core.context.registry.provideDelegate
import io.github.photowey.kttp.core.context.storage.Storage
import io.github.photowey.kttp.core.context.store.EnvironmentStore
import io.github.photowey.kttp.core.context.store.VariablesStore
import io.github.photowey.kttp.core.extension.auth.AuthExtensions
import io.github.photowey.kttp.core.extension.jsonpath.JsonPathExtension
import io.github.photowey.kttp.core.printer.Printer
import io.github.photowey.kttp.core.extension.jsonpath.JsonPathsImpl
import io.github.photowey.kttp.core.extension.auth.KttpAuthExtensionsImpl


/**
 * Exposes the services and resources used to register and execute a Kttp script.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
interface KttpContext : AutoCloseable, ComponentProvider {
    val environmentStore: EnvironmentStore
    val variablesStore: VariablesStore
    val cookiesContext: CookiesContext
    val clientContext: ClientContext
    val printer: Printer
    val jsonContext: JsonContext
    val executionContext: ExecutionContext
    val builderFactory: KttpBuilderFactory
    val jsonpath: JsonPathExtension
    val auth: AuthExtensions
    val storage: Storage
}

/**
 * Resolves script services from a component registry and closes owned resources.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpContextImpl(
    private val registry: ComponentRegistry = ComponentsRegistryImpl()
) : KttpContext,
    AutoCloseable,
    ComponentRegistry by registry {

    override var environmentStore: EnvironmentStore by registry
    override var variablesStore: VariablesStore by registry
    override var cookiesContext: CookiesContext by registry
    override var clientContext: ClientContext by registry
    override var printer: Printer by registry
    override var jsonContext: JsonContext by registry
    override var executionContext: ExecutionContext by registry
    override var builderFactory: KttpBuilderFactory by registry
    override var jsonpath: JsonPathExtension by registry
    override var auth: AuthExtensions by registry
    override var storage: Storage by registry

    override fun close() {
        try {
            registry.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Creates and wires a context that owns the supplied closeable services through its registry.
 * Close the returned context after script execution to release clients and flush persistent storage.
 */
fun createKttpContext(
    storage: Storage,
    environmentStore: EnvironmentStore,
    cookiesContext: CookiesContext,
    clientContext: ClientContext,
    printer: Printer,
): KttpContextImpl {
    val context = KttpContextImpl()
    context.apply {
        this.storage = storage
        this.environmentStore = environmentStore
        this.variablesStore = VariablesStore(storage)
        this.cookiesContext = cookiesContext
        this.clientContext = clientContext
        this.printer = printer
        jsonContext = JsonContext()
        executionContext = ExecutionContext()
        builderFactory = KttpBuilderFactoryImpl(context)
        jsonpath = JsonPathsImpl(context)
        auth = KttpAuthExtensionsImpl(context)
    }

    return context
}
