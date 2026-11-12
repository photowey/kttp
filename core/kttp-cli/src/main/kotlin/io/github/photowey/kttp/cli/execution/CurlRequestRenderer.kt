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

package io.github.photowey.kttp.cli.execution

import io.github.photowey.kttp.core.context.ClientContextImpl
import io.github.photowey.kttp.core.context.KttpContext
import io.github.photowey.kttp.core.context.execution.ExecutionRegistration
import io.github.photowey.kttp.core.context.execution.ExecutionScenario
import io.github.photowey.kttp.core.context.execution.result.RequestHolder
import io.github.photowey.kttp.core.context.store.NoEnvironmentSelectedException
import io.github.photowey.kttp.core.interceptor.KttpInterceptor
import io.github.photowey.kttp.core.printer.NoopPrinter

/**
 * Renders selected HTTP registrations without publishing a synthetic response to their result listeners.
 * Request construction uses the original context so prerequisite values can resolve normally; a separate
 * terminal client preserves application interceptors while stopping the exported request before transport.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/13
 */
internal class CurlRequestRenderer(private val context: KttpContext) {
    fun render(scenario: ExecutionScenario) {
        val registrations = when (scenario) {
            ExecutionScenario.File -> context.executionContext.registrations
            is ExecutionScenario.SingleExecution -> listOf(
                context.executionContext.findRegistration(scenario.declarationCoordinates)
            )
        }
        val interceptor = KttpInterceptor(NoopPrinter, null, curlMode = true, curlOutput = { println(it) })
        ClientContextImpl(interceptor, context.clientContext.globalConfigurer).use { clients ->
            registrations.filterNot { it.isIgnored }.forEach { render(it, clients) }
        }
    }

    private fun render(registration: ExecutionRegistration, clients: ClientContextImpl) {
        if (registration.envKeysUsedUnset.isNotEmpty()) {
            throw NoEnvironmentSelectedException(
                "No environment selected. This request uses environment variable(s): " +
                    registration.envKeysUsedUnset.joinToString(", ") + ". Select an environment before exporting."
            )
        }
        val holder = registration.executable as? RequestHolder
            ?: error("The selected operation is not an HTTP request and cannot be exported as cURL")
        val builder = holder.requestBuilderProvider.getRequestBuilder()
        // Do not call holder.execute(): its result listeners contain then/thenResponse callbacks.
        clients.getClient(builder.getClientConfigurer()).newCall(builder.build()).execute().close()
    }
}
