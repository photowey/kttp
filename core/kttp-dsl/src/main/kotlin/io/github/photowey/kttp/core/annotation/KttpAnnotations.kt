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

package io.github.photowey.kttp.core.annotation

/**
 * Marks receiver scopes that participate in the Kttp DSL.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
@DslMarker
annotation class KttpDSL

/**
 * Marks functions that declare HTTP requests.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
@Target(AnnotationTarget.FUNCTION)
annotation class RequestFunction

/**
 * Associates a request function with an HTTP method name.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
@Target(AnnotationTarget.FUNCTION)
annotation class RequestMapping(
    val method: String
)

/**
 * Marks a parameter representing a request path variable.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class PathVariable

/**
 * Marks a parameter representing a request query parameter.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class RequestParam

/**
 * Marks a parameter representing an HTTP header name.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class HeaderName

/**
 * Marks a header-value parameter and optionally identifies its associated header.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class HeaderValue(
    val ofHeader: String = ""
)

/**
 * Marks a parameter representing request header data.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class RequestHeader

/**
 * Marks a parameter representing an HTTP request body.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class RequestBody