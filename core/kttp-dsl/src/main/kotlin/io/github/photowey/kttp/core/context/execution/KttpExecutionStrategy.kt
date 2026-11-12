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

package io.github.photowey.kttp.core.context.execution

import com.moczul.ok2curl.CurlInterceptor
import com.moczul.ok2curl.logger.Logger
import io.github.photowey.kttp.core.context.KttpContext
import io.github.photowey.kttp.core.context.execution.result.ResultExecutable
import io.github.photowey.kttp.core.context.execution.result.MapFunction
import io.github.photowey.kttp.core.context.execution.result.MappedRequestHolder
import io.github.photowey.kttp.core.printer.println
import io.github.photowey.kttp.dsl.builder.RequestBuilder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Defines how a prepared request is executed with its client configuration.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
interface RequestExecutionStrategy {
    fun executeRequest(context: KttpContext, requestBuilder: RequestBuilder): Response
}

/**
 * Combines HTTP execution with the mapping of registered request holders.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
interface KttpExecutionStrategy : RequestExecutionStrategy {
    fun <R> mapRequestExecutable(
        context: KttpContext,
        requestExecutable: ResultExecutable<Response>,
        mapFunction: MapFunction<Response, R>
    ): MappedRequestHolder<R>
}

/**
 * Executes HTTP requests and retains their normal result-processing holders.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class DefaultExecutionStrategy : KttpExecutionStrategy {

    override fun executeRequest(context: KttpContext, requestBuilder: RequestBuilder): Response {
        val request = requestBuilder.build()
        val clientConfigurer = requestBuilder.getClientConfigurer()
        val client = context.clientContext.getClient(clientConfigurer)
        val response: Response = client.newCall(request).execute()
        return response
    }

    override fun <R> mapRequestExecutable(
        context: KttpContext,
        requestExecutable: ResultExecutable<Response>,
        mapFunction: MapFunction<Response, R>
    ): MappedRequestHolder<R> {
        return MappedRequestHolder(
            context,
            requestExecutable,
            mapFunction
        )
    }
}

/**
 * Renders requests through a cURL interceptor and substitutes result-processing holders.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class CurlExecutionStrategy : KttpExecutionStrategy {
    override fun executeRequest(context: KttpContext, requestBuilder: RequestBuilder): Response {
        val interceptor = simpleCurlInterceptor { command ->
            context.printer.println(command)
        }
        val executionStrategy = CurlRequestExecutionStrategy(interceptor)
        return executionStrategy.executeRequest(context, requestBuilder)
    }

    override fun <R> mapRequestExecutable(
        context: KttpContext,
        requestExecutable: ResultExecutable<Response>,
        mapFunction: MapFunction<Response, R>
    ): MappedRequestHolder<R> {
        return MappedRequestHolder(
            context,
            DummyRequestHolder,
            mapFunction
        )
    }

    /**
     * Provides a placeholder holder for results omitted by the cURL execution strategy.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    private object DummyRequestHolder : ResultExecutable<Response>() {
        override val originalExecutable: Executable<*>
            get() = throw UnsupportedOperationException()

        override fun doExecute() = throw UnsupportedOperationException()
    }
}

/**
 * Routes a prepared request through the configured cURL interceptor.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
private class CurlRequestExecutionStrategy(
    private val curlInterceptor: CurlInterceptor
) : RequestExecutionStrategy {

    override fun executeRequest(context: KttpContext, requestBuilder: RequestBuilder): Response {
        val request = requestBuilder.build()
        val client = context.clientContext.getClient {
            addInterceptor(curlInterceptor)

            addInterceptor {
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .message("")
                    .code(200)
                    .build()
            }
        }

        return client.newCall(request).execute()
    }
}

private fun simpleCurlInterceptor(doLog: (String) -> Unit) = CurlInterceptor(
    object : Logger {
        override fun log(message: String) = doLog(message)
    }
)
