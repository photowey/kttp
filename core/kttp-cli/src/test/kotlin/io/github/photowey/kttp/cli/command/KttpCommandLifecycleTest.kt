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

package io.github.photowey.kttp.cli.command

import com.sun.net.httpserver.HttpServer
import io.github.photowey.kttp.cli.model.CliExecutionResult
import io.github.photowey.kttp.core.context.CookiesContextImpl
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.InetSocketAddress
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies CLI persistence, response capture, and cleanup across successful and failed runs.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class KttpCommandLifecycleTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `storage-dir persists variables between CLI runs`() {
        val storage = directory.resolve("variables").toFile()
        val first = runCli("store", "vars.setValue(\"token\", \"persisted\")", "--storage-dir", storage.path)
        assertEquals(0, first.exitCode, first.output)
        assertTrue(storage.resolve("variables.json").isFile, "--storage-dir must create persistent storage")

        val second = runCli(
            "load",
            """
                val token = vars.getValue<String>("token", kotlin.reflect.typeOf<String>())
                check(token == "persisted") { "Stored token was not loaded: ${'$'}token" }
            """.trimIndent(),
            "--storage-dir", storage.path
        )

        assertEquals(0, second.exitCode, second.output)
        assertTrue(second.result.success, second.output)
    }

    @Test
    fun `cookies-file saves received cookies before successful exit`() {
        withCookieServer { url ->
            val cookiesFile = directory.resolve("success-cookies.json")
            val result = runCli("success", "GET(\"$url\")", "--cookies-file", cookiesFile.toString())

            assertEquals(0, result.exitCode, result.output)
            assertTrue(result.result.success, result.output)
            assertSavedCookie(cookiesFile, url)
            if (result.result.success) assertTrue(result.result.requestHeaders.keys.any { it.equals("Host", true) })
        }
    }

    @Test
    fun `cookies-file saves received cookies before failed exit`() {
        withCookieServer { url ->
            val cookiesFile = directory.resolve("failure-cookies.json")
            val script = "GET(\"$url\").then { error(\"failure after receiving cookie\") }"
            val result = runCli("failure", script, "--cookies-file", cookiesFile.toString())

            assertEquals(1, result.exitCode, result.output)
            assertFalse(result.result.success, result.output)
            assertTrue(result.result.message.contains("failure after receiving cookie"), result.output)
            assertEquals(200, result.result.statusCode, "A then failure must retain the HTTP response")
            assertTrue(File(result.result.responseFilePath!!).isFile, "A then failure must retain its response file")
            val metadata = kotlinx.serialization.json.Json.parseToJsonElement(
                result.stdout.lineSequence().last { it.isNotBlank() })
                as kotlinx.serialization.json.JsonObject
            assertTrue(
                metadata["httpTimeMs"]?.toString()?.toLongOrNull() != null,
                "HTTP time must be separate from script evaluation"
            )
            assertSavedCookie(cookiesFile, url)
            if (result.result.success) assertTrue(result.result.requestHeaders.keys.any { it.equals("Host", true) })
        }
    }

    @Test
    fun `large multibyte CLI response advertises its bounded preview and complete size`() {
        val bytes = "测试".repeat(40000).toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/multibyte.txt") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val result = runCli("multibyte", "GET(\"http://127.0.0.1:${server.address.port}/multibyte.txt\")")
            val json = kotlinx.serialization.json.Json.parseToJsonElement(
                result.stdout.lineSequence().last { it.isNotBlank() })
                as kotlinx.serialization.json.JsonObject
            assertEquals("true", json["bodyTruncated"].toString())
            assertEquals(bytes.size.toString(), json["responseSize"].toString())
            assertTrue(result.result.body!!.length < bytes.size)
            assertTrue(bytes.contentEquals(File(result.result.responseFilePath!!).readBytes()))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `last empty response cannot reuse the previous download association`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/large.txt") { exchange ->
            val bytes = ByteArray(96 * 1024) { 'x'.code.toByte() }
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/empty") { exchange -> exchange.sendResponseHeaders(204, -1); exchange.close() }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}"
            val result = runCli("last-empty", "GET(\"$url/large.txt\")\nHEAD(\"$url/empty\")")
            val json = kotlinx.serialization.json.Json.parseToJsonElement(
                result.stdout.lineSequence().last { it.isNotBlank() })
                as kotlinx.serialization.json.JsonObject
            assertEquals(204, result.result.statusCode)
            assertEquals("0", json["responseSize"].toString())
            assertTrue(json["bodyTruncated"]?.toString() != "true")
            assertTrue(result.result.responseFilePath?.endsWith("large.txt") != true)
            assertTrue(result.result.requestHeaders.keys.any { it.equals("Host", true) })
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `every empty response is saved and manifested before its callback`() {
        withEmptyResponseServer { url ->
            val manifest = directory.resolve("empty-responses.jsonl").toFile()
            val snapshot = directory.resolve("empty-snapshot.json").toFile()
            val source = """
                GET("$url/empty").then {
                    check(java.io.File("${manifest.path}").readLines().size == 1) { "204 was not manifested before then" }
                    val path = kotlinx.serialization.json.Json.decodeFromString<String>(java.io.File("${manifest.path}").readLines().single())
                    check(java.io.File("${snapshot.path}").readText().contains(path)) { "Snapshot has no saved response before then" }
                }
                HEAD("$url/head").then {
                    check(java.io.File("${manifest.path}").readLines().size == 2) { "HEAD was not manifested before then" }
                }
                GET("$url/zero").then {
                    check(java.io.File("${manifest.path}").readLines().size == 3) { "Empty 200 was not manifested before then" }
                }
            """.trimIndent()

            val run = runCli(
                "empty-manifest", source,
                "--response-manifest", manifest.path, "--response-snapshot", snapshot.path
            )

            assertEquals(0, run.exitCode, run.output)
            val saved = manifest.readLines().map { File(Json.decodeFromString<String>(it)) }
            assertEquals(3, saved.size)
            assertEquals(3, saved.distinct().size)
            assertTrue(saved.all { it.isFile && it.length() == 0L })
            assertEquals(saved.last().absolutePath, run.result.responseFilePath)
            assertEquals("", run.result.body)
            assertEquals("EMPTY", run.result.bodyType)
            assertEquals(
                3, directory.resolve("responses").toFile().listFiles()!!.size,
                "Final result must reuse the captured file instead of creating another response"
            )
        }
    }

    @Test
    fun `empty response manifest and snapshot survive a failed callback`() {
        withEmptyResponseServer { url ->
            val manifest = directory.resolve("failed-empty.jsonl").toFile()
            val snapshot = directory.resolve("failed-empty-snapshot.json").toFile()

            val run = runCli(
                "empty-failed", "GET(\"$url/empty\").then { error(\"empty callback failed\") }",
                "--response-manifest", manifest.path, "--response-snapshot", snapshot.path
            )

            assertEquals(1, run.exitCode, run.output)
            assertTrue(run.result.message.contains("empty callback failed"), run.output)
            val saved = manifest.readLines().map { File(Json.decodeFromString<String>(it)) }.single()
            assertTrue(saved.isFile)
            assertEquals(0L, saved.length())
            assertEquals(saved.absolutePath, run.result.responseFilePath)
            val captured = CliExecutionResult.fromJson(snapshot.readText())
            assertEquals(204, captured.statusCode)
            assertEquals(saved.absolutePath, captured.responseFilePath)
            assertEquals("", captured.body)
        }
    }

    private fun withEmptyResponseServer(block: (String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(if (exchange.requestURI.path == "/empty") 204 else 200, -1)
            exchange.close()
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }

    private fun assertSavedCookie(file: Path, url: String) {
        assertTrue(file.toFile().isFile, "--cookies-file must be saved before process exit")
        CookiesContextImpl(file).use { cookies ->
            val loaded = cookies.cookieJar.loadForRequest(url.toHttpUrl())
            assertEquals(listOf("session=saved"), loaded.map { "${it.name}=${it.value}" })
        }
    }

    private fun withCookieServer(block: (String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/cookie") { exchange ->
            exchange.responseHeaders.add("Set-Cookie", "session=saved; Path=/; Max-Age=3600")
            exchange.responseHeaders.add("Content-Type", "application/json")
            val body = "{}".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}/cookie")
        } finally {
            server.stop(0)
        }
    }

    private fun runCli(name: String, source: String, vararg options: String): CommandResult {
        val script = directory.resolve("$name.kttp.kts").toFile().apply { writeText(source) }
        val output = directory.resolve("$name.out").toFile()
        val errors = directory.resolve("$name.err").toFile()
        val command = listOf(
            File(System.getProperty("java.home"), "bin/java").absolutePath,
            "-Xmx512m", "-cp", runtimeClasspath(),
            "io.github.photowey.kttp.cli.command.KttpCommandKt",
            "--script", script.absolutePath, "--json-output", "--no-compilation-cache",
            "--response-dir", directory.resolve("responses").toString()
        ) + options
        val process = ProcessBuilder(command).redirectOutput(output).redirectError(errors).start()
        if (!process.waitFor(90, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor()
            error("CLI did not exit within 90 seconds: ${output.readText()}\n${errors.readText()}")
        }
        return CommandResult(process.exitValue(), output.readText(), errors.readText())
    }

    private fun runtimeClasspath(): String {
        val loaderPaths = generateSequence(javaClass.classLoader) { it.parent }
            .filterIsInstance<URLClassLoader>()
            .flatMap { it.getURLs().asSequence() }
            .filter { it.protocol == "file" }
            .map { File(it.toURI()).absolutePath }
        return (System.getProperty("java.class.path").split(File.pathSeparator).asSequence() + loaderPaths)
            .distinct().joinToString(File.pathSeparator)
    }

    /**
     * Captures a CLI process exit code and its standard output and error streams.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    private data class CommandResult(val exitCode: Int, val stdout: String, val stderr: String) {
        val output get() = "$stdout\n$stderr"
        val result get() = CliExecutionResult.fromJson(stdout.lineSequence().last { it.isNotBlank() })
    }
}
