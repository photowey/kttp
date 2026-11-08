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

package io.github.photowey.kttp.core.context.execution.result

import io.github.photowey.kttp.core.context.KttpContext
import io.github.photowey.kttp.core.context.execution.Executable
import io.github.photowey.kttp.dsl.builder.RequestBuilder
import okhttp3.Response

/**
 * Publishes execution results to registered listeners.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
abstract class ResultExecutable<T>() : Executable<T>() {

    abstract val originalExecutable: Executable<*>

    protected val listeners = mutableListOf<ExecutionListener<T>>()

    protected fun fireResult(result: T) {
        listeners.forEach { listener ->
            listener.onResultObtained(result)
        }
    }

    /**
     * Executes the operation and synchronously notifies listeners in order.
     * An operation or listener failure propagates; listeners after a failing listener are not called.
     */
    final override fun execute(): T {
        val result = doExecute()
        fireResult(result)
        return result
    }

    protected abstract fun doExecute(): T

    fun addExecutionListener(listener: ExecutionListener<T>) {
        listeners.add(listener)
    }
}

/**
 * Builds and executes an HTTP request using its Kttp context and execution strategy.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class RequestHolder(
    val requestBuilderProvider: RequestBuilderProvider,
    val context: KttpContext
) : ResultExecutable<Response>() {
    override val originalExecutable = this

    private val executionStrategy
        get() = context.executionContext.getExecutionStrategy(this)

    override fun doExecute(): Response {
        val requestBuilder = requestBuilderProvider.getRequestBuilder()
        val response = executionStrategy.executeRequest(context, requestBuilder)
        return response
    }

    /** Attaches a response mapping through the current execution strategy without sending the request here. */
    fun <R> mapWithThen(mapFunction: MapFunction<Response, R>): MappedRequestHolder<R> =
        executionStrategy.mapRequestExecutable(context, this, mapFunction)
}

/** Use for direct Response access in block (e.g. body!!.string()). For assertStatus/decode use then { }. */
infix fun <R> RequestHolder.thenResponse(mapFunction: MapFunction<Response, R>): MappedRequestHolder<R> =
    this.mapWithThen(mapFunction)

/**
 * Maps an HTTP response through a callback and retains the resulting value.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class MappedRequestHolder<R>(
    private val context: KttpContext,
    private val originRequestHolder: ResultExecutable<Response>,
    private val mapFunction: MapFunction<Response, R>
) : ResultExecutable<R>() {
    override val originalExecutable = originRequestHolder

    private var response: Response? = null
    private var result: R? = null

    init {
        originRequestHolder.onResultObtained(::handleResponse)
    }

    private fun handleResponse(obtainedResponse: Response) {
        response = obtainedResponse
        val newResult = mapFunction(obtainedResponse)
        result = newResult
        fireResult(newResult)
    }

    /**
     * Reuses a non-null mapped value, otherwise executes the origin if no response has arrived.
     * After successful dependent execution, the origin is skipped in the later scenario pass.
     * A null mapping result cannot satisfy this holder and causes an IllegalArgumentException.
     */
    override fun doExecute(): R {
        result?.let { return it }

        if (response == null) {
            originRequestHolder.execute()
            context.executionContext.ignoreOnExecutionPhase(originRequestHolder)
        }

        return requireNotNull(result)
    }

    /**
     * Returns the response received from the original executable, even if mapping subsequently fails.
     * Null until its result listener receives a response; it may be set before this holder executes.
     */
    fun getLastResponse(): Response? = response
}

/**
 * Transforms a receiver value into a mapped result.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
typealias MapFunction<L, R> = L.() -> R

/**
 * Receives the result produced by an executable.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
interface ExecutionListener<T> {
    fun onResultObtained(result: T)
}

/** Registers a synchronous result listener; this does not execute the operation or replay an earlier result. */
fun <T> ResultExecutable<T>.onResultObtained(handleResult: (T) -> Unit) {
    this.addExecutionListener(object : ExecutionListener<T> {
        override fun onResultObtained(result: T) {
            handleResult(result)
        }
    })
}

/**
 * Creates the request builder when an HTTP registration is evaluated.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
fun interface RequestBuilderProvider {
    fun getRequestBuilder(): RequestBuilder
}
