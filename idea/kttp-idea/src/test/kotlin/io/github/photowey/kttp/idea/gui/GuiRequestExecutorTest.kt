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

package io.github.photowey.kttp.idea.gui

import com.sun.net.httpserver.HttpServer
import io.github.photowey.kttp.core.enums.HttpMethod
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies direct GUI HTTP execution, response capture, request values, and cancellation.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class GuiRequestExecutorTest {
    @Test
    fun persistenceFailureKeepsTheAlreadyReceivedResponse() {
        withServer { server ->
            server.createContext("/") { exchange ->
                exchange.sendResponseHeaders(200, 2)
                exchange.responseBody.use { it.write("ok".toByteArray()) }
            }
            val notDirectory = Files.createTempFile("kttp-cannot-save-response", ".txt")
            try {
                val request = RequestSpec(target = RequestTarget(ValueRef.Literal("http://127.0.0.1:${server.address.port}/")))
                var delivered = false
                val result = GuiRequestExecutor.execute(
                    GuiDraft("failure", "Save failure", DraftOwnership.DETACHED, request), emptyMap(), null, notDirectory,
                    onResponsePreview = { delivered = true }
                )
                assertTrue(delivered)
                assertTrue(!result.success)
                assertEquals(200, result.statusCode)
                assertEquals("ok", result.body)
                assertEquals(null, result.responseFilePath)
            } finally {
                Files.deleteIfExists(notDirectory)
            }
        }
    }
    @Test
    fun previewIsDeliveredBeforeResponseStorageIsCreated() {
        withServer { server ->
            val text = "x".repeat(192 * 1024)
            server.createContext("/early.txt") { exchange ->
                exchange.sendResponseHeaders(200, text.length.toLong())
                exchange.responseBody.use { it.write(text.toByteArray()) }
            }
            val directory = Files.createTempDirectory("kttp-early-preview")
            var previews = 0
            try {
                val request = RequestSpec(target = RequestTarget(ValueRef.Literal("http://127.0.0.1:${server.address.port}/early.txt")))
                val result = GuiRequestExecutor.execute(
                    GuiDraft("early", "Early response", DraftOwnership.DETACHED, request), emptyMap(), null, directory,
                    onResponsePreview = { preview ->
                        previews++
                        assertEquals(200, preview.statusCode)
                        assertEquals(text.take(64 * 1024), preview.body)
                        assertEquals(null, preview.responseFilePath)
                        assertTrue(directory.toFile().listFiles().isNullOrEmpty(), "Preview cannot wait for file persistence")
                    }
                )
                assertEquals(1, previews)
                assertTrue(result.success, result.message)
                assertEquals(text, java.io.File(requireNotNull(result.responseFilePath)).readText())
            } finally {
                directory.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun terminalRedirectKeepsTheCompleteBodyAndMarksItsPreview() {
        withServer { server ->
            val bytes = ByteArray(128 * 1024) { 'r'.code.toByte() }
            server.createContext("/redirect.txt") { exchange ->
                exchange.responseHeaders.add("Location", "/unrequested")
                exchange.sendResponseHeaders(302, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            val directory = Files.createTempDirectory("kttp-terminal-redirect")
            try {
                val request = RequestSpec(
                    target = RequestTarget(ValueRef.Literal("http://127.0.0.1:${server.address.port}/redirect.txt")),
                    transport = TransportSpec(noRedirect = true)
                )
                val result = GuiRequestExecutor.execute(
                    GuiDraft("redirect", "Redirect", DraftOwnership.DETACHED, request), emptyMap(), null, directory
                )
                assertTrue(result.success, result.message)
                assertEquals(302, result.statusCode)
                assertTrue(result.bodyTruncated, "A terminal redirect has the same preview boundary as any response")
                assertEquals(bytes.size.toLong(), result.responseSize)
                assertTrue(bytes.contentEquals(Files.readAllBytes(java.nio.file.Path.of(result.responseFilePath!!))))
            } finally {
                directory.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun largeResponseHasABoundedPreviewAndACompleteSavedFile() {
        withServer { server ->
            val bytes = ByteArray(192 * 1024) { 'x'.code.toByte() }
            server.createContext("/download.txt") { exchange ->
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            val directory = Files.createTempDirectory("kttp-large-response-test")
            try {
                val request = RequestSpec(
                    target = RequestTarget(
                        ValueRef.Literal(
                            "http://127.0.0.1:${server.address.port}/download.txt"
                        )
                    )
                )
                val result = GuiRequestExecutor.execute(
                    GuiDraft("large", "Download", DraftOwnership.DETACHED, request), emptyMap(), null, directory
                )
                assertTrue(result.success, result.message)
                assertTrue(result.body!!.length <= 64 * 1024)
                assertTrue(result.bodyTruncated)
                assertEquals(bytes.size.toLong(), result.responseSize)
                assertTrue(bytes.contentEquals(Files.readAllBytes(java.nio.file.Path.of(result.responseFilePath!!))))
            } finally {
                directory.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun cancellationBeforeCallCreationPreventsTheRequest() {
        withServer { server ->
            var received = false
            server.createContext("/") { exchange -> received = true; exchange.close() }
            val directory = Files.createTempDirectory("kttp-cancel-before-send")
            try {
                val cancellation = GuiRequestCancellation().apply { cancel() }
                val request =
                    RequestSpec(target = RequestTarget(ValueRef.Literal("http://127.0.0.1:${server.address.port}/")))
                val result = GuiRequestExecutor.execute(
                    GuiDraft("cancel", "Cancel", DraftOwnership.DETACHED, request),
                    emptyMap(),
                    null,
                    directory,
                    cancellation
                )
                assertTrue(!result.success)
                assertEquals("Request cancelled", result.message)
                assertTrue(!received)
                assertTrue(directory.toFile().listFiles().isNullOrEmpty())
            } finally {
                directory.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun jsonBodyPreservesExplicitContentTypeWithoutAddingAnotherHeader() {
        withServer { server ->
            var contentTypes = emptyList<String>()
            var actualBody = ""
            server.createContext("/") { exchange ->
                contentTypes = exchange.requestHeaders["Content-Type"].orEmpty()
                actualBody = exchange.requestBody.bufferedReader().readText()
                exchange.sendResponseHeaders(200, 2)
                exchange.responseBody.use { it.write("ok".toByteArray()) }
            }
            val request = RequestSpec(
                method = HttpMethod.POST,
                target = RequestTarget(ValueRef.Literal("http://127.0.0.1:${server.address.port}/")),
                headers = listOf(
                    HeaderSpec(
                        "content-type",
                        ValueRef.Literal("application/vnd.kttp+json; charset=utf-8")
                    )
                ),
                body = BodySpec.Json(ValueRef.Literal("{\"key\":42}"))
            )
            val result = GuiRequestExecutor.execute(
                GuiDraft("test", "JSON", DraftOwnership.DETACHED, request), emptyMap(), null,
                Files.createTempDirectory("kttp-response-test")
            )
            assertTrue(result.success, result.message)
            assertEquals(listOf("application/vnd.kttp+json; charset=utf-8"), contentTypes)
            assertEquals("{\"key\":42}", actualBody)
        }
    }

    @Test
    fun optionalMissingBodyFileMatchesScriptDelegateEmptyValue() {
        withServer { server ->
            var received = false
            server.createContext("/") { exchange ->
                assertEquals("", exchange.requestBody.bufferedReader().readText())
                received = true
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
            }
            val request = RequestSpec(
                method = HttpMethod.POST,
                target = RequestTarget(ValueRef.Literal("http://127.0.0.1:${server.address.port}/")),
                body = BodySpec.Raw(ValueRef.LocalFile("missing.txt", required = false))
            )
            val result = GuiRequestExecutor.execute(
                GuiDraft("test", "Optional file", DraftOwnership.DETACHED, request),
                emptyMap(),
                Files.createTempDirectory("kttp-missing-test").toString(),
                Files.createTempDirectory("kttp-response-test")
            )
            assertTrue(result.success, result.message)
            assertTrue(received)
        }
    }

    private fun withServer(test: (HttpServer) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        try {
            test(server)
        } finally {
            server.stop(0)
        }
    }
}
