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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies bounded response capture, complete downloads, compression, and failure cleanup.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class KttpInterceptorResponseTest {
    @TempDir
    lateinit var temporaryDir: Path

    @Test
    fun `large response is saved completely while capture stays bounded and script body remains readable`() {
        val bytes = ByteArray(3 * 1024 * 1024) { (it % 251).toByte() }
        var preview: ByteArray? = null
        var saved: String? = null
        val interceptor = KttpInterceptor(
            NoopPrinter, temporaryDir,
            onResponseCaptured = { preview = it.body!!.bytes() },
            onResponseFileSaved = { saved = it }
        )

        execute(interceptor, bytes.toResponseBody()).use { response ->
            val captured = assertNotNull(preview)
            assertTrue(captured.size <= 64 * 1024, "Capture must not retain the whole download")
            assertContentEquals(bytes.copyOf(captured.size), captured)
            assertContentEquals(bytes, Files.readAllBytes(Path.of(assertNotNull(saved))))
            assertContentEquals(bytes, response.body!!.bytes(), "Script sees the complete response")
        }
    }

    @Test
    fun `response without storage returns after a bounded preview instead of draining the stream`() {
        val source = CountingSource(4L * 1024 * 1024)
        val body = object : ResponseBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = -1L
            override fun source() = source.buffered
        }
        var previewSize = 0
        val interceptor = KttpInterceptor(
            NoopPrinter, null,
            onResponseCaptured = { previewSize = it.body!!.bytes().size }
        )

        execute(interceptor, body).use { response ->
            assertTrue(previewSize in 1..64 * 1024)
            assertTrue(source.bytesRead <= 72 * 1024, "An unbounded stream must not be drained to capture it")
            assertEquals(4 * 1024 * 1024, response.body!!.bytes().size)
        }
        assertTrue(source.closed)
    }

    @Test
    fun `gzip download is stored decoded with bounded preview and retains original bytes for script transport`() {
        val bytes = "large compressed response\n".repeat(10000).toByteArray()
        val compressed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(bytes) }
        }.toByteArray()
        var preview: ByteArray? = null
        var earlyPreview: ByteArray? = null
        var saved: String? = null
        val interceptor = KttpInterceptor(
            NoopPrinter, temporaryDir,
            onResponseCaptured = { preview = it.body!!.bytes() },
            onResponseFileSaved = { saved = it },
            onResponsePreview = {
                earlyPreview = it.body!!.bytes()
                assertEquals(null, saved, "The preview must precede file publication")
            }
        )

        execute(interceptor, compressed.toResponseBody(), encoding = "gzip").use { response ->
            val captured = assertNotNull(preview)
            assertContentEquals(captured, assertNotNull(earlyPreview))
            assertTrue(captured.size in 1..64 * 1024)
            assertContentEquals(bytes.copyOf(captured.size), captured)
            assertContentEquals(bytes, Files.readAllBytes(Path.of(assertNotNull(saved))))
            assertContentEquals(compressed, response.body!!.bytes())
        }
        Files.list(temporaryDir).use { assertEquals(1L, it.count(), "Replay spool is removed on close") }
    }

    @Test
    fun `server filenames cannot write outside response directory or through dangling symlink`() {
        val responses = Files.createDirectory(temporaryDir.resolve("responses"))
        val outside = temporaryDir.resolve("outside.txt")
        val symlinkTarget = temporaryDir.resolve("symlink-target.txt")
        Files.createSymbolicLink(responses.resolve("link.txt"), symlinkTarget)
        val names = listOf("../outside.txt", outside.toString(), "..\\outside.txt", "link.txt")

        for (name in names) {
            var saved: String? = null
            val interceptor = KttpInterceptor(NoopPrinter, responses, onResponseFileSaved = { saved = it })
            execute(interceptor, "safe".toResponseBody(), name).close()
            val path = Path.of(assertNotNull(saved))
            assertEquals(responses, path.toAbsolutePath().normalize().parent)
            assertFalse(Files.isSymbolicLink(path))
            assertEquals("safe", Files.readString(path))
        }
        assertFalse(Files.exists(outside), "Traversal must not create a sibling file")
        assertFalse(Files.exists(symlinkTarget), "Saving must never follow a dangling symlink")
    }

    @Test
    fun `simultaneous downloads keep distinct files and never overwrite an existing response`() {
        Files.writeString(temporaryDir.resolve("download.txt"), "original")
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val tasks = (1..24).map { index ->
                executor.submit(Callable {
                    start.await()
                    var saved: String? = null
                    val interceptor = KttpInterceptor(NoopPrinter, temporaryDir, onResponseFileSaved = { saved = it })
                    execute(interceptor, "payload-$index".toResponseBody(), "download.txt").close()
                    Path.of(assertNotNull(saved)) to "payload-$index"
                })
            }
            start.countDown()
            val results = tasks.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(24, results.map { it.first }.toSet().size)
            results.forEach { (path, expected) -> assertEquals(expected, Files.readString(path)) }
            assertEquals("original", Files.readString(temporaryDir.resolve("download.txt")))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `failed body read closes the source and removes the partial file`() {
        var sourceClosed = false
        var saved = false
        val source = object : Source {
            private var started = false

            override fun read(sink: Buffer, byteCount: Long): Long {
                if (started) throw IOException("Interrupted download")
                started = true
                sink.writeUtf8("partial")
                return 7L
            }

            override fun timeout() = Timeout.NONE
            override fun close() {
                sourceClosed = true
            }
        }
        val interceptor = KttpInterceptor(NoopPrinter, temporaryDir, onResponseFileSaved = { saved = true })

        assertFailsWith<IOException> { execute(interceptor, source.buffer().asResponseBody()) }

        assertTrue(sourceClosed)
        assertFalse(saved)
        Files.list(temporaryDir).use { assertEquals(0L, it.count()) }
    }

    @Test
    fun `invalid gzip response removes both partial output and temporary replay`() {
        var saved = false
        val interceptor = KttpInterceptor(NoopPrinter, temporaryDir, onResponseFileSaved = { saved = true })

        assertFailsWith<IOException> {
            execute(interceptor, "not gzip".toResponseBody(), encoding = "gzip")
        }

        assertFalse(saved)
        Files.list(temporaryDir).use { assertEquals(0L, it.count()) }
    }

    @Test
    fun `capture callback failure closes temporary gzip replay but keeps completed response bytes`() {
        val bytes = "complete response".toByteArray()
        val compressed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(bytes) }
        }.toByteArray()
        val interceptor = KttpInterceptor(
            NoopPrinter, temporaryDir,
            onResponseCaptured = { throw IllegalStateException("Callback failed") }
        )

        assertFailsWith<IllegalStateException> {
            execute(interceptor, compressed.toResponseBody(), encoding = "gzip")
        }

        val files = Files.list(temporaryDir).use { it.toList() }
        assertEquals(1, files.size)
        assertContentEquals(bytes, Files.readAllBytes(files.single()))
    }

    @Test
    fun `empty responses with gzip metadata save empty files without decompression`() {
        val savedPaths = mutableListOf<String>()
        for ((method, status) in listOf("HEAD" to 200, "GET" to 204, "GET" to 200)) {
            var preview: ByteArray? = null
            val interceptor = KttpInterceptor(
                NoopPrinter, temporaryDir,
                onResponseCaptured = { preview = it.body!!.bytes() },
                onResponseFileSaved = { savedPaths.add(it) }
            )

            execute(interceptor, ByteArray(0).toResponseBody(), encoding = "gzip", method = method, status = status)
                .use { response ->
                    assertEquals(status, response.code)
                    assertContentEquals(ByteArray(0), assertNotNull(preview))
                    assertContentEquals(ByteArray(0), response.body!!.bytes())
                }
        }
        assertEquals(3, savedPaths.distinct().size)
        savedPaths.forEach { assertEquals(0L, Files.size(Path.of(it))) }
        Files.list(temporaryDir).use { assertEquals(3L, it.count()) }
    }

    @Test
    fun `empty response without a storage directory does not save a file`() {
        var saved = false
        val interceptor = KttpInterceptor(NoopPrinter, null, onResponseFileSaved = { saved = true })

        execute(interceptor, ByteArray(0).toResponseBody(), status = 204).use {
            assertContentEquals(ByteArray(0), it.body!!.bytes())
        }

        assertFalse(saved)
        Files.list(temporaryDir).use { assertEquals(0L, it.count()) }
    }

    private fun execute(
        interceptor: KttpInterceptor,
        body: ResponseBody,
        filename: String? = null,
        encoding: String? = null,
        method: String = "GET",
        status: Int = 200
    ): Response {
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(status)
                    .message("OK")
                    .apply { filename?.let { header("Content-Disposition", "attachment; filename=\"$it\"") } }
                    .apply { encoding?.let { header("Content-Encoding", it) } }
                    .body(body)
                    .build()
            }
            .build()
        return client.newCall(Request.Builder().url("http://localhost/download").method(method, null).build()).execute()
    }

    /**
     * Provides a synthetic response source that records consumed bytes and closure.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    private class CountingSource(private val size: Long) : Source {
        var bytesRead = 0L
        var closed = false
        val buffered = buffer()

        override fun read(sink: Buffer, byteCount: Long): Long {
            val count = minOf(byteCount, size - bytesRead)
            if (count == 0L) return -1
            sink.write(ByteArray(count.toInt()))
            bytesRead += count
            return count
        }

        override fun timeout() = Timeout.NONE
        override fun close() {
            closed = true
        }
    }
}
