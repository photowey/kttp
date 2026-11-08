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

package io.github.photowey.kttp.core.extension.auth

import io.github.photowey.kttp.core.annotation.RequestFunction
import io.github.photowey.kttp.core.auth.AuthData
import io.github.photowey.kttp.core.auth.OAuthRunner
import io.github.photowey.kttp.dsl.ValueDelegate
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KProperty

/**
 * Exposes OAuth operations and delegated access to authentication results.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
interface AuthExtensions {
    @RequestFunction
    @ApiStatus.Experimental
    fun oauth(
        authorizeEndpoint: String,
        clientId: String,
        clientSecret: String?,
        scope: String,
        tokenEndpoint: String,
        redirectUri: String
    ): OAuthRunner

    operator fun OAuthRunner.provideDelegate(
        @Suppress("unused")
        receiver: Any?,
        prop: KProperty<*>
    ): ValueDelegate<AuthData>
}