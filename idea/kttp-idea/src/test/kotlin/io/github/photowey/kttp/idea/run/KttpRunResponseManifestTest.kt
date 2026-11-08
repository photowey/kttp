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

package io.github.photowey.kttp.idea.run

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies process-specific response manifests and rejection of unrelated or invalid response paths.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class KttpRunResponseManifestTest {
    @Test
    fun manifestRejectsMissingAndOutOfDirectoryPathsAndMalformedRecords() {
        val directory = Files.createTempDirectory("kttp-run-manifest").toFile()
        val outside = Files.createTempFile("kttp-outside", ".json").toFile()
        try {
            val first = directory.resolve("first response.json").apply { writeText("first") }
            val second = directory.resolve("second.json").apply { writeText("second") }
            val manifest = directory.resolve("manifest.jsonl")
            manifest.writeText(
                listOf(
                    Json.encodeToString(first.absolutePath), "not JSON",
                    Json.encodeToString(outside.absolutePath),
                    Json.encodeToString(directory.resolve("missing.json").absolutePath),
                    Json.encodeToString(second.absolutePath)
                ).joinToString("\n")
            )
            assertEquals(listOf(first, second), KttpRunResponseManifest.read(manifest, directory))
        } finally {
            outside.delete()
            directory.deleteRecursively()
        }
    }

    @Test
    fun fakeStdoutWithoutAnHttpRequestCannotClaimAnExistingResponse() {
        val directory = Files.createTempDirectory("kttp-run-fake-output").toFile()
        try {
            val responses = directory.resolve("responses").apply { mkdirs() }
            val old = responses.resolve("old.json").apply { writeText("old response") }
            val script = directory.resolve("fake.kttp.kts").apply {
                writeText("println(\"Response file saved.\")\nprintln(\"> ${old.absolutePath}\")\n")
            }
            val manifest = directory.resolve("fake.jsonl")
            val run = startCli(script, responses, manifest)
            awaitSuccess(run)
            assertTrue(run.output.readText().contains("Response file saved."))
            assertEquals(emptyList(), KttpRunResponseManifest.read(manifest, responses))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun concurrentCliProcessesKeepTheirOwnOrderedResponseManifests() {
        val directory = Files.createTempDirectory("kttp-run-concurrent").toFile()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            val responses = directory.resolve("responses").apply { mkdirs() }
            val old = responses.resolve("old.json").apply { writeText("old response") }
            val fakeBody = "Response file saved.\n> ${old.absolutePath}"
            server.createContext("/") { exchange ->
                val body = if (exchange.requestURI.path == "/fake") fakeBody else exchange.requestURI.path
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "text/plain")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
                exchange.close()
            }
            server.start()
            val url = "http://127.0.0.1:${server.address.port}"
            val firstScript = directory.resolve("first.kttp.kts").apply {
                writeText("GET(\"$url/first\")\nGET(\"$url/fake\")\n")
            }
            val secondScript = directory.resolve("second.kttp.kts").apply { writeText("GET(\"$url/second\")\n") }
            val firstManifest = directory.resolve("first.jsonl")
            val secondManifest = directory.resolve("second.jsonl")
            val firstRun = startCli(firstScript, responses, firstManifest)
            val secondRun = startCli(secondScript, responses, secondManifest)
            try {
                awaitSuccess(firstRun)
                awaitSuccess(secondRun)
                val firstFiles = KttpRunResponseManifest.read(firstManifest, responses)
                val secondFiles = KttpRunResponseManifest.read(secondManifest, responses)
                assertEquals(listOf("/first", fakeBody), firstFiles.map { it.readText() })
                assertEquals(listOf("/second"), secondFiles.map { it.readText() })
                assertTrue(firstFiles.none { it in secondFiles || it == old })
                assertTrue(secondFiles.none { it == old })
            } finally {
                firstRun.process.destroyForcibly()
                secondRun.process.destroyForcibly()
            }
        } finally {
            server.stop(0)
            directory.deleteRecursively()
        }
    }

    /**
     * Pairs a running CLI process with the file collecting its output.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    private data class CliRun(val process: Process, val output: File)

    private fun startCli(script: File, responses: File, manifest: File): CliRun {
        val output = script.resolveSibling(script.nameWithoutExtension + ".log")
        val process = ProcessBuilder(
            File(System.getProperty("java.home"), "bin/java").absolutePath,
            "-jar", requireNotNull(System.getProperty("kttp.test.cliJar")),
            "--script", script.absolutePath, "--response-dir", responses.absolutePath,
            "--response-manifest", manifest.absolutePath, "--no-compilation-cache"
        ).redirectErrorStream(true).redirectOutput(output).start()
        return CliRun(process, output)
    }

    private fun awaitSuccess(run: CliRun) {
        if (!run.process.waitFor(60, TimeUnit.SECONDS)) {
            run.process.destroyForcibly()
            error("CLI timed out: ${run.output.readText()}")
        }
        assertEquals(0, run.process.exitValue(), run.output.readText())
    }
}
