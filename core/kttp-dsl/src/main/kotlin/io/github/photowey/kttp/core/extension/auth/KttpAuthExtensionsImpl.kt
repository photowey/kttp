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

import io.github.photowey.kttp.core.auth.AuthData
import io.github.photowey.kttp.core.auth.OAuthRunner
import io.github.photowey.kttp.core.context.KttpContext
import io.github.photowey.kttp.core.context.execution.DeclarationCoordinates
import io.github.photowey.kttp.dsl.AbstractValueDelegate
import io.github.photowey.kttp.dsl.ValueDelegate
import kotlin.reflect.KProperty

/**
 * Creates OAuth operations and connects authentication results to the script context.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpAuthExtensionsImpl(private val context: KttpContext) : AuthExtensions {
    override fun oauth(
        authorizeEndpoint: String,
        clientId: String,
        clientSecret: String?,
        scope: String,
        tokenEndpoint: String,
        redirectUri: String
    ): OAuthRunner {
        val authRunner = OAuthRunner(
            authorizeEndpoint,
            clientId,
            scope,
            tokenEndpoint,
            clientSecret,
            redirectUri,
            context
        )

        context.executionContext.registerExecutable(
            authRunner,
            null
        )

        return authRunner
    }

    override fun OAuthRunner.provideDelegate(
        receiver: Any?,
        prop: KProperty<*>
    ): ValueDelegate<AuthData> {
        val oAuthRunner = this
        val coordinates = DeclarationCoordinates(prop.name)
        context.executionContext.addCoordinatesForExecutable(coordinates, oAuthRunner)
        return AuthValueDelegate(prop, this, context)
    }
}

/**
 * Retrieves stored authentication data for a delegated OAuth result.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class AuthValueDelegate(
    private val prop: KProperty<*>,
    private val runner: OAuthRunner,
    context: KttpContext
) : AbstractValueDelegate<AuthData>() {
    private val key = prop.name
    private val storeMap = context.variablesStore

    init {
        runner.addListener(object : OAuthRunner.Listener {
            override fun onAuthorized(auth: AuthData) {
                storeMap.setValue(key, auth)
            }
        })
        runner.storedAuthProvider = {
            storeMap.getValue<AuthData>(key, prop.returnType)
        }
    }

    override fun getValueImpl(
        thisRef: Any?,
        property: KProperty<*>
    ): AuthData = runner.getAuth()
}
