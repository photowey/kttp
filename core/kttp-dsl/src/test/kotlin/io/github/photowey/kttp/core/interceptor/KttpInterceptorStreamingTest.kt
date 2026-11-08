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

package io.github.photowey.kttp.core.interceptor

import io.github.photowey.kttp.core.printer.NoopPrinter
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.io.TempDir
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Verifies cancellation and total timeouts for continuously streaming responses.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class KttpInterceptorStreamingTest {
    @TempDir
    lateinit var temporaryDir: Path

    @Test
    fun `cancelling a continuously active download closes it and does not publish a partial response`() {
        assertStreamTerminates(cancel = true)
    }

    @Test
    fun `call timeout bounds a continuously active download even when read timeout never expires`() {
        assertStreamTerminates(cancel = false)
    }

    @Test
    fun `preview is available while download continues and cancellation removes partial files`() {
        StreamingServer().use { server ->
            val preview = CountDownLatch(1)
            var savedCount = 0
            val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS)
                .addNetworkInterceptor(KttpInterceptor(NoopPrinter, temporaryDir,
                    onResponseFileSaved = { savedCount++ },
                    onResponsePreview = {
                        assertEquals(200, it.code)
                        assertTrue(it.body!!.string().isNotEmpty())
                        preview.countDown()
                    })).build()
            val call = client.newCall(Request.Builder().url(server.url).build())
            val executor = Executors.newSingleThreadExecutor()
            try {
                val execution = executor.submit { call.execute().use { it.body!!.bytes() } }
                assertTrue(preview.await(3, TimeUnit.SECONDS), "A preview cannot require the full stream")
                assertTrue(!execution.isDone)
                call.cancel()
                assertFailsWith<ExecutionException> { execution.get(3, TimeUnit.SECONDS) }
                assertEquals(0, savedCount)
                Files.list(temporaryDir).use { assertEquals(0L, it.count()) }
            } finally {
                call.cancel()
                executor.shutdownNow()
                client.connectionPool.evictAll()
            }
        }
    }

    private fun assertStreamTerminates(cancel: Boolean) {
        StreamingServer().use { server ->
            var savedCount = 0
            val client = OkHttpClient.Builder()
                .callTimeout(if (cancel) 5 else 1, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .addNetworkInterceptor(
                    KttpInterceptor(
                    NoopPrinter, temporaryDir,
                    onResponseFileSaved = { savedCount++ }
                ))
                .build()
            val call = client.newCall(Request.Builder().url(server.url).build())
            val executor = Executors.newSingleThreadExecutor()
            try {
                val execution = executor.submit { call.execute().use { it.body!!.bytes() } }
                assertTrue(server.streaming.await(2, TimeUnit.SECONDS), "Server must start streaming")
                if (cancel) call.cancel()
                val error = assertFailsWith<ExecutionException> { execution.get(4, TimeUnit.SECONDS) }
                assertIs<IOException>(error.cause)
                assertEquals(0, savedCount, "Only complete downloads may be published")
                Files.list(temporaryDir).use { assertEquals(0L, it.count(), "Partial files must be removed") }
            } finally {
                call.cancel()
                executor.shutdownNow()
                client.connectionPool.evictAll()
            }
        }
    }

    /**
     * Serves a continuous HTTP response stream for cancellation and timeout tests.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    private class StreamingServer : Closeable {
        private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        private val socket = AtomicReference<Socket?>()
        val streaming = CountDownLatch(1)
        val url = "http://localhost:${server.localPort}/stream"
        private val worker = thread(isDaemon = true, name = "kttp-streaming-regression") {
            runCatching {
                server.accept().use { connection ->
                    socket.set(connection)
                    val reader = connection.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) Unit
                    val output = connection.getOutputStream()
                    output.write(
                        ("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n" +
                            "Transfer-Encoding: chunked\r\nConnection: close\r\n\r\n").toByteArray()
                    )
                    val chunk = "400\r\n" + "x".repeat(1024) + "\r\n"
                    while (!Thread.currentThread().isInterrupted) {
                        output.write(chunk.toByteArray())
                        output.flush()
                        streaming.countDown()
                        Thread.sleep(10)
                    }
                }
            }
        }

        override fun close() {
            server.close()
            socket.get()?.close()
            worker.interrupt()
            worker.join(1000)
        }
    }
}
