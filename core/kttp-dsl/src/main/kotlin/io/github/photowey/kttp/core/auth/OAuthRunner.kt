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

package io.github.photowey.kttp.core.auth

import com.sun.net.httpserver.HttpServer
import io.github.photowey.kttp.core.context.KttpContext
import io.github.photowey.kttp.core.context.execution.Executable
import io.github.photowey.kttp.core.extension.jsonpath.doRead
import io.github.photowey.kttp.core.printer.Printer
import io.github.photowey.kttp.core.printer.println
import io.github.photowey.kttp.dsl.builder.RequestBuilder
import java.net.InetSocketAddress
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.CompletableFuture

/**
 * Normalizes the alternative refresh-token lifetime fields returned by OAuth providers.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
@Suppress("PropertyName")
interface RefreshTokenInfo {
    val refresh_token_expires_in: Long?

    val refresh_expires_in: Long?

    fun getRefreshExpiresUnified(): Long {
        return refresh_token_expires_in
            ?: refresh_expires_in
            ?: error("No refresh expiration field")
    }
}

/**
 * Represents the OAuth token endpoint response to an authorization-code exchange.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
@Suppress("PropertyName")
private data class AccessTokenResponse(
    val access_token: String,
    val expires_in: Long,
    val refresh_token: String,
    val scope: String,
    val token_type: String,
    override val refresh_token_expires_in: Long?,
    override val refresh_expires_in: Long?
) : RefreshTokenInfo

/**
 * Represents the OAuth token endpoint response to a refresh-token exchange.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
@Suppress("PropertyName")
private data class RefreshTokenResponse(
    val access_token: String,
    val expires_in: Long,
    val scope: String,
    val token_type: String,
    override val refresh_token_expires_in: Long?,
    override val refresh_expires_in: Long?,
) : RefreshTokenInfo

/**
 * Coordinates OAuth authorization, token refresh, and stored authentication data for a script.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class OAuthRunner(
    private val authorizeEndpoint: String,
    private val clientId: String,
    private val scope: String,
    private val tokenEndpoint: String,
    private val clientSecret: String?,
    private val redirectUri: String,
    private val context: KttpContext
) : Executable<AuthData>() {

    var storedAuthProvider: () -> AuthData? = { null }

    private val jsonContext = context.jsonContext

    private fun refresh(auth: AuthData): AuthData {
        callListeners {
            it.beforeRefreshTokenCall()
        }
        val response = context.executionContext
            .getExecutionStrategy(this)
            .executeRequest(
                context,
                RequestBuilder(
                    "POST",
                    tokenEndpoint,
                    context
                ).apply {
                    headers("Content-Type" to "application/x-www-form-urlencoded")

                    formData {
                        field("client_id", clientId)

                        if (clientSecret != null) {
                            field("client_secret", clientSecret)
                        }

                        field("refresh_token", auth.refreshToken)
                        field("grant_type", "refresh_token")
                    }
                }
            )

        if (response.code != 200) {
            context.printer.println("Failed to refresh token", color = Printer.Color.RED)
            throw RuntimeException("Execution exception")
        }

        val refreshTokenResponse = jsonContext
            .getReadContext(response)
            .doRead<RefreshTokenResponse>("$")

        println(refreshTokenResponse)

        val newAuth = AuthData(
            refreshTokenResponse.access_token,
            auth.refreshToken,
            System.currentTimeMillis() + refreshTokenResponse.expires_in * 1000,
            System.currentTimeMillis() + refreshTokenResponse.getRefreshExpiresUnified() * 1000
        )
        callListeners {
            it.onTokenRefreshed(newAuth)
        }
        return newAuth
    }

    private fun authorize(): AuthData {
        val authUrl = "$authorizeEndpoint?" +
            "client_id=$clientId" +
            "&response_type=code" +
            "&redirect_uri=${redirectUri}" +
            "&scope=${
                URLEncoder.encode(
                    scope,
                    "UTF-8"
                )
            }" +
            "&access_type=offline" +
            "&prompt=consent"

        context.printer.println(authUrl)

        val authCode = waitForAuthCode(redirectUri, authUrl)

        val response = context.executionContext
            .getExecutionStrategy(this)
            .executeRequest(
                context,
                RequestBuilder("POST", tokenEndpoint, context).apply {
                    headers("Content-Type" to "application/x-www-form-urlencoded")

                    formData {
                        field("client_id", clientId)

                        if (clientSecret != null) {
                            field("client_secret", clientSecret)
                        }

                        field("code", authCode)
                        field("redirect_uri", redirectUri)
                        field("grant_type", "authorization_code")
                    }
                }
            )

        require(response.code == 200) {
            "Failed to get access token: $response"
        }
        val accessTokenResponse = jsonContext.getReadContext(response).doRead<AccessTokenResponse>("$")

        val auth = AuthData(
            accessTokenResponse.access_token,
            accessTokenResponse.refresh_token,
            System.currentTimeMillis() + accessTokenResponse.expires_in * 1000,
            System.currentTimeMillis() + accessTokenResponse.getRefreshExpiresUnified() * 1000
        )
        callListeners { it.onAuthorized(auth) }
        return auth
    }

    /**
     * Returns valid stored authentication, refreshes an expired or explicitly requested access token,
     * or starts authorization when no usable refresh token exists. This may block for HTTP and user authorization.
     */
    fun getAuth(withAccessTokenRefresh: Boolean = false): AuthData {
        val storedAuth = storedAuthProvider()
        return when {
            storedAuth == null -> authorize()
            storedAuth.isRefreshTokenExpired() -> authorize()
            storedAuth.isAccessTokenExpired() -> refresh(storedAuth)
            withAccessTokenRefresh -> refresh(storedAuth)
            else -> storedAuth
        }
    }

    /** Obtains authentication with access-token refresh requested even when the stored token has not expired. */
    override fun execute(): AuthData {
        return getAuth(true)
    }

    private fun waitForAuthCode(
        redirectUri: String,
        authUrl: String
    ): String {
        val redirectUrl = java.net.URI.create(redirectUri).toURL()
        val port = redirectUrl.port
        val path = redirectUrl.path

        val future = CompletableFuture<String>()
        val server = HttpServer.create(InetSocketAddress(port), 0)

        server.createContext(path) { exchange ->
            val uri: URI = exchange.requestURI
            val query = uri.rawQuery ?: ""
            val params = query.split("&").associate {
                val (k, v) = it.split("=")
                k to v
            }

            val code = params["code"]

            val response = if (code != null) {
                "Authorization successful! You can close this tab."
            } else {
                future.completeExceptionally(IllegalArgumentException("No 'code' in query"))
                "Missing 'code' parameter"
            }

            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }

            if (code != null) {
                future.complete(code)
            }
        }

        server.start()
        println("Waiting for authorization ...")
        callListeners { it.onWaitAuthCode(authUrl) }

        val code = future.get()
        server.stop(0)
        return code
    }

    private fun callListeners(action: (Listener) -> Unit) =
        listeners.asReversed().forEach(action)

    private val listeners: MutableList<Listener> = mutableListOf()

    fun addListener(listener: Listener) {
        listeners += listener
    }

    /**
     * Receives callbacks around authorization and token refresh operations.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    interface Listener {
        fun onAuthorized(auth: AuthData) {}

        fun onWaitAuthCode(authUrl: String) {}

        fun beforeRefreshTokenCall() {}
        fun onTokenRefreshed(auth: AuthData) {}
    }
}

private fun AuthData.isAccessTokenExpired() = System.currentTimeMillis() >= accessTokenExpirationTs
private fun AuthData.isRefreshTokenExpired() = System.currentTimeMillis() >= refreshTokenExpirationTs
