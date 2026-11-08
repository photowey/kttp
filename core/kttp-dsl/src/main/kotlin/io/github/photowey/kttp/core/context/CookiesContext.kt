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

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.photowey.kttp.core.context.storage.AtomicStorageFile
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.Closeable
import java.io.IOException
import java.io.Serializable
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Supplies the cookie jar used by HTTP requests.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
interface CookiesContext {
    val cookieJar: CookieJar
}

/**
 * Supplies a cookie jar that does not retain cookies.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
object NoopCookiesContext : CookiesContext {
    override val cookieJar: CookieJar = CookieJar.NO_COOKIES
}

/**
 * Owns a file-backed cookie jar and persists it when closed.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class CookiesContextImpl(storage: Path) : CookiesContext, AutoCloseable {
    private val cookieJarImpl = PersistentCookieJar(storage)
    override val cookieJar: CookieJar = cookieJarImpl

    override fun close() {
        cookieJarImpl.close()
    }
}

/**
 * Tracks cookies by identity, enforces their scope and expiry, and persists the archive.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
private class PersistentCookieJar(private val filePath: Path) : CookieJar, Closeable {
    private val objectMapper = ObjectMapper()
    private val cookiesMap = ConcurrentHashMap<CookieIdentity, Cookie>()

    init {
        readCookies().forEach(::saveCookie)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        removeExpired()
        return cookiesMap.values.filter { it.matches(url) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach(::saveCookie)
    }

    private fun saveCookie(cookie: Cookie) {
        val identity = CookieIdentity(cookie.name, cookie.domain, cookie.path)
        if (cookie.expiresAt <= System.currentTimeMillis()) cookiesMap.remove(identity)
        else cookiesMap[identity] = cookie
    }

    private fun removeExpired() {
        val now = System.currentTimeMillis()
        cookiesMap.values.removeIf { it.expiresAt <= now }
    }

    private fun readCookies(): List<Cookie> {
        val content = AtomicStorageFile.read(filePath) ?: return emptyList()
        return try {
            objectMapper.factory.createParser(content).use { parser ->
                if (parser.nextToken() == null) return emptyList()
                val storage = objectMapper.readerFor(CookiesStorage::class.java)
                    .with(FAIL_ON_TRAILING_TOKENS)
                    .readValue<CookiesStorage?>(parser)
                    ?: throw IOException("Cookie storage must contain a JSON object")
                storage.cookies.map { it.toCookie() }
            }
        } catch (failure: IOException) {
            throw IOException("Cannot parse cookie storage: ${filePath.toAbsolutePath()}", failure)
        } catch (failure: IllegalArgumentException) {
            throw IOException("Invalid cookie in storage: ${filePath.toAbsolutePath()}", failure)
        }
    }

    /**
     * Removes expired cookies and atomically persists this jar after verifying the existing archive is readable.
     * This writes the current jar snapshot; it does not merge cookies from other sessions.
     */
    override fun close() {
        // A file damaged after opening must remain available for recovery too.
        readCookies()
        removeExpired()
        val value = CookiesStorage(cookiesMap.values.map(::SerializableCookie))
        AtomicStorageFile.replace(filePath) { objectMapper.writeValue(it.toFile(), value) }
    }
}

/**
 * Identifies a cookie by its name, domain, and path.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
private data class CookieIdentity(val name: String, val domain: String, val path: String)

/**
 * Represents the serialized collection of persisted cookies.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
data class CookiesStorage(val cookies: List<SerializableCookie> = emptyList()) : Serializable

/**
 * Stores the cookie attributes needed to reconstruct an OkHttp cookie.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
data class SerializableCookie(
    val name: String = "",
    val value: String = "",
    val domain: String = "",
    val path: String = "",
    val expiresAt: Long = 0L,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    // Legacy archives lack this flag; keep their previous exact-host lookup scope.
    val hostOnly: Boolean = true
) : Serializable {

    constructor(cookie: Cookie) : this(
        name = cookie.name,
        value = cookie.value,
        domain = cookie.domain,
        path = cookie.path,
        expiresAt = cookie.expiresAt,
        secure = cookie.secure,
        httpOnly = cookie.httpOnly,
        hostOnly = cookie.hostOnly
    )

    fun toCookie(): Cookie = Cookie.Builder()
        .name(name)
        .value(value)
        .apply { if (hostOnly) hostOnlyDomain(domain) else domain(domain) }
        .path(path)
        .expiresAt(expiresAt)
        .apply {
            if (secure) secure()
            if (httpOnly) httpOnly()
        }
        .build()
}
