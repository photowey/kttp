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

import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_16BE
import java.nio.charset.StandardCharsets.UTF_16LE
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies cookie identity, scope, expiration, persistence, and archive error handling.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class CookiesContextTest {
    @TempDir
    lateinit var directory: Path

    private val origin = "https://api.example.com/".toHttpUrl()
    private val sibling = "https://other.example.com/".toHttpUrl()
    private val file get() = directory.resolve("cookies.json")

    @Test
    fun `new cookie replaces previous value for the same name domain and path`() {
        CookiesContextImpl(file).use { context ->
            context.save(origin, "session=old; Path=/; Max-Age=3600")
            context.save(origin, "session=new; Path=/; Max-Age=3600")

            assertEquals(listOf("new"), context.values(origin))
        }
        CookiesContextImpl(file).use { assertEquals(listOf("new"), it.values(origin)) }
    }

    @Test
    fun `same name cookies retain their independent paths and domains`() {
        CookiesContextImpl(file).use { context ->
            context.save(origin, "session=root; Path=/; Max-Age=3600")
            context.save(origin, "session=admin; Path=/admin; Max-Age=3600")
            context.save(origin, "session=parent; Domain=example.com; Path=/; Max-Age=3600")

            assertEquals(
                setOf("root", "admin", "parent"),
                context.values("https://api.example.com/admin".toHttpUrl()).toSet()
            )
            assertEquals(setOf("root", "parent"), context.values(origin).toSet())
            assertEquals(listOf("parent"), context.values(sibling))
        }
    }

    @Test
    fun `expired replacement deletes only the matching cookie identity`() {
        CookiesContextImpl(file).use { context ->
            context.save(origin, "session=root; Path=/; Max-Age=3600")
            context.save(origin, "session=admin; Path=/admin; Max-Age=3600")
            context.save(origin, "session=deleted; Path=/; Max-Age=0")

            assertEquals(emptyList(), context.values(origin))
            assertEquals(listOf("admin"), context.values("https://api.example.com/admin".toHttpUrl()))
        }
        CookiesContextImpl(file).use { assertEquals(emptyList(), it.values(origin)) }
    }

    @Test
    fun `expired cookie from storage is neither returned nor persisted again`() {
        Files.writeString(
            file, """
            {"cookies":[{"name":"session","value":"expired","domain":"api.example.com",
            "path":"/","expiresAt":1,"secure":false,"httpOnly":false}]}
        """.trimIndent()
        )

        CookiesContextImpl(file).use { assertEquals(emptyList(), it.values(origin)) }

        assertFalse(Files.readString(file).contains("expired"))
    }

    @Test
    fun `parent domain cookie matches subdomains consistently before and after reload`() {
        CookiesContextImpl(file).use { context ->
            context.save(origin, "session=parent; Domain=example.com; Path=/; Max-Age=3600")

            assertEquals(listOf("parent"), context.values(origin))
            assertEquals(listOf("parent"), context.values(sibling))
            assertEquals(emptyList(), context.values("https://notexample.com/".toHttpUrl()))
        }

        CookiesContextImpl(file).use { context ->
            assertEquals(listOf("parent"), context.values(origin))
            assertEquals(listOf("parent"), context.values(sibling))
        }
    }

    @Test
    fun `host only cookie keeps its scope after persistence`() {
        CookiesContextImpl(file).use { it.save(origin, "session=host; Path=/; Max-Age=3600") }

        CookiesContextImpl(file).use { context ->
            val restored = context.cookieJar.loadForRequest(origin).single()
            assertTrue(restored.hostOnly)
            assertEquals(emptyList(), context.values("https://nested.api.example.com/".toHttpUrl()))
        }
    }

    @Test
    fun `legacy cookie json without host only remains restricted to its saved host`() {
        Files.writeString(
            file, """
            {"cookies":[{"name":"session","value":"legacy","domain":"api.example.com",
            "path":"/","expiresAt":253402300799999,"secure":true,"httpOnly":true}]}
        """.trimIndent()
        )

        CookiesContextImpl(file).use { context ->
            assertEquals(listOf("legacy"), context.values(origin))
            assertEquals(emptyList(), context.values("https://nested.api.example.com/".toHttpUrl()))
            assertEquals(emptyList(), context.values(sibling))
            val restored = context.cookieJar.loadForRequest(origin).single()
            assertTrue(restored.secure)
            assertTrue(restored.httpOnly)
            assertEquals(emptyList(), context.values("http://api.example.com/".toHttpUrl()))
        }

        CookiesContextImpl(file).use { context ->
            assertEquals(listOf("legacy"), context.values(origin))
            assertEquals(emptyList(), context.values("https://nested.api.example.com/".toHttpUrl()))
        }
    }

    @Test
    fun `malformed cookie storage fails explicitly without changing its bytes`() {
        val corrupt = "{\"cookies\":[BROKEN]}"
        Files.writeString(file, corrupt)

        assertFailsWith<IOException> { CookiesContextImpl(file) }

        assertEquals(corrupt, Files.readString(file))
    }

    @Test
    fun `invalid cookie archive shape or trailing content cannot be silently accepted`() {
        for (invalid in listOf("null", "[]", "{\"cookies\":[]} trailing")) {
            Files.writeString(file, invalid)

            assertFailsWith<IOException>(invalid) { CookiesContextImpl(file) }

            assertEquals(invalid, Files.readString(file))
        }
    }

    @Test
    fun `cookie corruption introduced during a session is preserved on close`() {
        val context = CookiesContextImpl(file)
        context.save(origin, "session=new; Path=/; Max-Age=3600")
        val corrupt = "{\"cookies\":[BROKEN]}"
        Files.writeString(file, corrupt)

        assertFailsWith<IOException> { context.close() }

        assertEquals(corrupt, Files.readString(file))
    }

    @Test
    fun `missing cookie storage creates its parent directory on save`() {
        val target = directory.resolve("new/nested/cookies.json")
        CookiesContextImpl(target).use { it.save(origin, "session=nested; Path=/; Max-Age=3600") }

        CookiesContextImpl(target).use { assertEquals(listOf("nested"), it.values(origin)) }
    }

    @Test
    fun `UTF8 BOM cookie archive remains readable and writable`() {
        assertEncodedArchive(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()), UTF_8)
    }

    @Test
    fun `UTF16 little endian BOM cookie archive remains readable and writable`() {
        assertEncodedArchive(byteArrayOf(0xff.toByte(), 0xfe.toByte()), UTF_16LE)
    }

    @Test
    fun `UTF16 big endian BOM cookie archive remains readable and writable`() {
        assertEncodedArchive(byteArrayOf(0xfe.toByte(), 0xff.toByte()), UTF_16BE)
    }

    @Test
    fun `empty or whitespace cookie archive is an empty writable store`() {
        for (content in listOf("", " \n\t\r")) {
            Files.write(file, content.toByteArray(UTF_8))
            CookiesContextImpl(file).use { context ->
                assertEquals(emptyList(), context.values(origin))
                context.save(origin, "session=new; Path=/; Max-Age=3600")
            }
            CookiesContextImpl(file).use { assertEquals(listOf("new"), it.values(origin)) }
        }
    }

    private fun assertEncodedArchive(bom: ByteArray, charset: Charset) {
        val archive = """
            {"cookies":[{"name":"session","value":"encoded","domain":"api.example.com",
            "path":"/","expiresAt":253402300799999,"hostOnly":true}]}
        """.trimIndent()
        Files.write(file, bom + archive.toByteArray(charset))
        CookiesContextImpl(file).use { assertEquals(listOf("encoded"), it.values(origin)) }
        CookiesContextImpl(file).use { assertEquals(listOf("encoded"), it.values(origin)) }
    }

    private fun CookiesContextImpl.save(url: HttpUrl, header: String) {
        cookieJar.saveFromResponse(url, listOf(requireNotNull(Cookie.parse(url, header))))
    }

    private fun CookiesContextImpl.values(url: HttpUrl): List<String> = cookieJar.loadForRequest(url).map { it.value }
}
