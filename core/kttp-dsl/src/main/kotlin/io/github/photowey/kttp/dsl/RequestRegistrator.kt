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

package io.github.photowey.kttp.dsl

import io.github.photowey.kttp.core.annotation.PathVariable
import io.github.photowey.kttp.core.annotation.RequestMapping
import io.github.photowey.kttp.core.annotation.RequestFunction
import io.github.photowey.kttp.core.annotation.RequestParam
import io.github.photowey.kttp.dsl.builder.RequestBuilder

/**
 * Registers requests through method-specific DSL functions and a generic request operation.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
interface RequestRegistrator<R> {

    /**
     * Registers a lazy request holder; the request configuration runs when the holder is executed.
     * Registering a request alone does not send HTTP traffic. Reading a delegated result can trigger execution.
     *
     * @param method an HTTP method
     * @param path request URL
     * @see GET
     * @see POST
     * @see OPTIONS
     * @see PUT
     * @see PATCH
     * @see DELETE
     * @see HEAD
     * @see TRACE
     */
    @RequestFunction
    fun request(
        method: String,
        @PathVariable path: String,
        @RequestParam name: String? = null,
        configure: RequestBuilder.() -> Unit = {}
    ): R
}

@RequestFunction
@RequestMapping("OPTIONS")
fun <R> RequestRegistrator<R>.OPTIONS(
    @PathVariable path: String,
    @RequestParam name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("OPTIONS", path, name, configure)

@RequestFunction
@RequestMapping("POST")
fun <R> RequestRegistrator<R>.POST(
    @PathVariable path: String,
    @RequestParam name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("POST", path, name, configure)

@RequestFunction
@RequestMapping("GET")
fun <R> RequestRegistrator<R>.GET(
    @PathVariable path: String,
    @RequestParam name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("GET", path, name, configure)

@RequestFunction
@RequestMapping("PUT")
fun <R> RequestRegistrator<R>.PUT(
    @PathVariable path: String,
    @RequestParam name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("PUT", path, name, configure)

@RequestFunction
@RequestMapping("PATCH")
fun <R> RequestRegistrator<R>.PATCH(
    @PathVariable path: String,
    @RequestParam name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("PATCH", path, name, configure)

@RequestFunction
@RequestMapping("DELETE")
fun <R> RequestRegistrator<R>.DELETE(
    @PathVariable path: String,
    @RequestParam name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("DELETE", path, name, configure)

@RequestFunction
@RequestMapping("HEAD")
fun <R> RequestRegistrator<R>.HEAD(
    @PathVariable path: String,
    @RequestParam name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("HEAD", path, name, configure)

@RequestFunction
@RequestMapping("TRACE")
fun <R> RequestRegistrator<R>.TRACE(
    @PathVariable path: String,
    @RequestParam name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("TRACE", path, name, configure)
