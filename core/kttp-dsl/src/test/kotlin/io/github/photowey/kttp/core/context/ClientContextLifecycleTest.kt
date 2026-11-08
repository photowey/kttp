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

import com.sun.net.httpserver.HttpServer
import io.github.photowey.kttp.core.interceptor.KttpInterceptor
import io.github.photowey.kttp.core.printer.NoopPrinter
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Verifies request capture, cURL interception, redirects, timeouts, and response-body ownership.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class ClientContextLifecycleTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `curl observes application rewrites without sending an HTTP request`() {
        withServer { server ->
            val received = AtomicInteger()
            server.createContext("/") { exchange -> received.incrementAndGet(); exchange.close() }
            var command: String? = null
            ClientContextImpl(KttpInterceptor(NoopPrinter, directory, curlMode = true, curlOutput = { command = it }))
                .use { context ->
                    val client = context.getClient {
                        addInterceptor { chain ->
                            chain.proceed(
                                chain.request().newBuilder()
                                    .url(chain.request().url.newBuilder().encodedPath("/changed").build())
                                    .header("X-Required", "rewritten").build()
                            )
                        }
                    }
                    client.newCall(Request.Builder().url("http://127.0.0.1:${server.address.port}/original").build())
                        .execute().close()
                    assertTrue(command.orEmpty().contains("/changed"), command)
                    assertTrue(command.orEmpty().contains("X-Required"), command)
                    assertEquals(0, received.get())
                }
        }
    }

    @Test
    fun `request capture observes application rewrites and automatically added wire headers`() {
        withServer { server ->
            var captured: io.github.photowey.kttp.core.interceptor.CapturedRequest? = null
            server.createContext("/rewritten") { exchange ->
                assertEquals("rewritten", exchange.requestBody.bufferedReader().readText())
                exchange.sendResponseHeaders(200, 2)
                exchange.responseBody.use { it.write("ok".toByteArray()) }
            }
            ClientContextImpl(KttpInterceptor(NoopPrinter, directory, onRequestCaptured = { captured = it }))
                .use { context ->
                    val client = context.getClient {
                        addInterceptor { chain ->
                            val changed = chain.request().newBuilder()
                                .url(chain.request().url.newBuilder().encodedPath("/rewritten").build())
                                .post("rewritten".toRequestBody())
                                .build()
                            chain.proceed(changed)
                        }
                    }
                    client.newCall(Request.Builder().url("http://127.0.0.1:${server.address.port}/original").build())
                        .execute().use { assertEquals("ok", it.body!!.string()) }
                    assertEquals("rewritten", captured!!.bodyString)
                    assertTrue(captured!!.url.endsWith("/rewritten"))
                    assertTrue(captured!!.headers.keys.any { it.equals("Host", true) })
                }
        }
    }

    @Test
    fun `context owns returned bodies until scripts finish then releases their sources`() {
        withServer { server ->
            val bytes = java.io.ByteArrayOutputStream().also { output ->
                GZIPOutputStream(output).use { it.write("complete body".toByteArray()) }
            }.toByteArray()
            server.createContext("/data.json") { exchange ->
                exchange.responseHeaders.add("Content-Encoding", "gzip")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            val context = ClientContextImpl(KttpInterceptor(NoopPrinter, directory))
            val response = context.getClient(NoopClientConfigurer).newCall(
                Request.Builder()
                    .url("http://127.0.0.1:${server.address.port}/data.json").build()
            ).execute()
            val body = response.body!!
            assertTrue(body.source().isOpen)
            context.close()
            assertFailsWith<IllegalStateException> { body.source().request(1) }
            Files.list(directory).use { files ->
                assertTrue(files.noneMatch { it.fileName.toString().startsWith(".kttp-replay-") })
            }
        }
    }

    @Test
    fun `only final redirect response is captured and shared default has a total timeout`() {
        withServer { server ->
            val captures = AtomicInteger()
            server.createContext("/start") { exchange ->
                exchange.responseHeaders.add("Location", "/final.txt")
                exchange.sendResponseHeaders(302, 1)
                exchange.responseBody.use { it.write(byteArrayOf(1)) }
            }
            server.createContext("/final.txt") { exchange ->
                exchange.sendResponseHeaders(200, 2)
                exchange.responseBody.use { it.write("ok".toByteArray()) }
            }
            ClientContextImpl(
                KttpInterceptor(
                    NoopPrinter, directory,
                    onResponseCaptured = { captures.incrementAndGet() })
            ).use { context ->
                val client = context.getClient(NoopClientConfigurer)
                assertTrue(client.callTimeoutMillis > 0, "Continuous streams need a total call deadline")
                client.newCall(Request.Builder().url("http://127.0.0.1:${server.address.port}/start").build())
                    .execute().use { assertEquals("ok", it.body!!.string()) }
                assertEquals(1, captures.get())
            }
        }
    }

    private fun withServer(block: (HttpServer) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        try {
            block(server)
        } finally {
            server.stop(0)
        }
    }
}
