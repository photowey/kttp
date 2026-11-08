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

import io.github.photowey.kttp.core.interceptor.KttpInterceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import java.util.concurrent.ConcurrentHashMap
import java.io.Closeable
import java.util.concurrent.TimeUnit

/**
 * Provides configured HTTP clients and owns their execution resources.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
interface ClientContext : Closeable {
    fun getClient(configure: ClientConfigurer): OkHttpClient
    var globalConfigurer: ClientConfigurer
}

/**
 * Builds and tracks OkHttp clients and response bodies for a Kttp execution context.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class ClientContextImpl(
    private val interceptor: KttpInterceptor,
    override var globalConfigurer: ClientConfigurer = NoopClientConfigurer
) : ClientContext {

    private val clients = ConcurrentHashMap.newKeySet<OkHttpClient>()
    private val responseBodies = ConcurrentHashMap.newKeySet<ResponseBody>()

    private val defaultClient = OkHttpClient.Builder()
        .callTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .addInterceptor { chain ->
            interceptor.interceptFinalResponse(chain).also { response -> response.body?.let(responseBodies::add) }
        }
        .addNetworkInterceptor { chain ->
            chain.proceed(interceptor.observeOutgoingRequest(chain.request()))
        }
        .buildAndRegister()

    /**
     * Builds a tracked client, applying the global configuration before the request configuration.
     * Clients and captured response bodies remain owned by this context until close.
     */
    override fun getClient(configure: ClientConfigurer): OkHttpClient {
        val client = defaultClient.newBuilder()
        client.globalConfigurer()
        client.configure()
        // Stop CURL before the transport, after script-provided application interceptors.
        if (interceptor.isCurlMode) client.addInterceptor(interceptor)
        return client.buildAndRegister()
    }

    /** Closes tracked bodies, cancels client calls, and evicts connections; individual cleanup failures are suppressed. */
    override fun close() {
        responseBodies.forEach { body -> runCatching { body.close() } }
        responseBodies.clear()
        for (client in clients) {
            runCatching {
                client.dispatcher.cancelAll()
                client.connectionPool.evictAll()
            }
        }
    }

    private fun OkHttpClient.Builder.buildAndRegister(): OkHttpClient =
        build().also(clients::add)
}

/**
 * Configures an OkHttp client builder using a receiver lambda.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
typealias ClientConfigurer = OkHttpClient.Builder.() -> Unit

val NoopClientConfigurer: ClientConfigurer = { }
