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

package io.github.photowey.kttp.idea.importing

import com.sun.net.httpserver.HttpServer
import io.github.photowey.kttp.idea.gui.GuiRequestExecutor
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.test.*

/**
 * Executes generated imports against a controlled HTTP server, including GUI and script representations.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
class ImportHttpIntegrationTest {
    @Test
    fun postmanFormPreservesRepeatedFieldsAndUtf8InBothModes() = withServer { base, received, dir ->
        val collection =
            """{"info":{"name":"Form","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},"item":[{"name":"Form","request":{"method":"POST","url":"$base/form","body":{"mode":"urlencoded","urlencoded":[{"key":"q","value":"hello world"},{"key":"q","value":"a+b"},{"key":"unicode","value":"你好"}]}}}]}"""
        val request = PostmanImportParser().parse(ImportInput(collection)).requests.single()
        runScript(ImportScriptRenderer.render(request), dir)
        val draft = assertNotNull(ImportRequestAdapter.adapt(request))
        val result =
            GuiRequestExecutor.execute(draft, emptyMap(), dir.absolutePath, dir.resolve("gui-response").toPath())
        assertTrue(result.success, result.message)
        assertEquals(2, received.size)
        received.forEach { captured ->
            assertEquals("POST", captured.method)
            assertEquals("/form", captured.path)
            val fields = captured.body.split('&').map { field ->
                URLDecoder.decode(field.substringBefore('='), "UTF-8") to
                    URLDecoder.decode(field.substringAfter('='), "UTF-8")
            }
            assertEquals(listOf("q" to "hello world", "q" to "a+b", "unicode" to "你好"), fields)
            assertTrue(captured.headers.entries.first { it.key.equals("Content-Type", true) }
                .value.single().startsWith("application/x-www-form-urlencoded"))
        }
    }

    @Test
    fun browserJsonHasTheSameMethodBodyHeadersAndQueryInBothModes() = withServer { base, received, dir ->
        val command =
            """curl '$base/echo?x=a%2Bb&x=two' -X POST -H 'X-Repeat: one' -H 'X-Repeat: two' --json '{"literal":"${'$'}value"}'"""
        val bundle = CurlImportParser().parse(ImportInput(command))
        assertTrue(bundle.diagnostics.none { it.severity == ImportSeverity.ERROR }, bundle.diagnostics.toString())
        val request = bundle.requests.single()
        val source = ImportScriptRenderer.render(request)
        runScript(source, dir)
        val gui = assertNotNull(ImportRequestAdapter.adapt(request))
        val result = GuiRequestExecutor.execute(gui, emptyMap(), dir.absolutePath, dir.resolve("gui-response").toPath())
        assertTrue(result.success, result.message)
        assertEquals(2, received.size)
        received.forEach {
            assertEquals("POST", it.method)
            assertEquals("x=a%2Bb&x=two", it.query)
            assertEquals("{\"literal\":\"${'$'}value\"}", it.body)
            assertEquals(listOf("one", "two"), it.headers.entries.first { h -> h.key.equals("X-Repeat", true) }.value)
            assertEquals(
                "application/json",
                it.headers.entries.first { h -> h.key.equals("Content-Type", true) }.value.single()
            )
        }
    }

    @Test
    fun postmanMixedEnvironmentAndCustomMultipartCompileAndExecute() = withServer { base, received, dir ->
        val postman =
            """{"info":{"name":"Demo","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},"item":[{"name":"Read","request":{"method":"GET","url":"{{baseUrl}}/users/{{id}}","auth":{"type":"bearer","bearer":[{"key":"token","value":"token-value"}]},"variable":[{"key":"id","value":"42"}]}}]}"""
        val first = PostmanImportParser().parse(ImportInput(postman)).requests.single()
        dir.resolve("payload.bin").writeBytes(byteArrayOf(0, 1, 2, 65))
        val second = ImportedRequest(
            "Upload", "CUSTOM", ImportValue("$base/upload"), body = ImportBody.Multipart(
                listOf(
                    ImportPart("note", ImportValue("sample")),
                    ImportPart("file", ImportValue("payload.bin"), true, "application/octet-stream")
                )
            )
        )
        val one = ImportScriptRenderer.render(first)
        val two = ImportScriptRenderer.render(second, one, dir.absolutePath)
        runScript(one + "\n" + two, dir, listOf("--env-param", "baseUrl=$base"))
        assertEquals(2, received.size)
        assertEquals("/users/42", received[0].path)
        assertEquals(
            "Bearer token-value",
            received[0].headers.entries.first { it.key.equals("Authorization", true) }.value.single()
        )
        assertEquals("CUSTOM", received[1].method)
        assertTrue(received[1].body.contains("sample"))
        assertTrue(received[1].body.contains("filename=\"payload.bin\""), received[1].body)
        assertTrue(received[1].body.contains("\u0000\u0001\u0002A"))
    }

    @Test
    fun mixedTemplatesPreserveLiteralDollarsKeywordsAndSharedBindingsOnTheWire() = withServer { base, received, dir ->
        val request = ImportedRequest(
            "Mixed", "POST", ImportValue("{{baseUrl}}/echo?x={{for}}", true),
            headers = listOf(ImportField("X-Punctuation", ImportValue("a-{{token-name}}-b", true))),
            body = ImportBody.Text(ImportValue("price=\$5;ref={{token-name}};quote=\"ok\";literal=\${danger}", true))
        )
        val first = ImportScriptRenderer.render(request)
        val second = ImportScriptRenderer.render(request, first)
        runScript(
            first + second,
            dir,
            listOf("--env-param", "baseUrl=$base", "--env-param", "for=42", "--env-param", "token-name=token")
        )
        assertEquals(2, received.size)
        received.forEach {
            assertEquals("x=42", it.query)
            assertEquals("price=\$5;ref=token;quote=\"ok\";literal=\${danger}", it.body)
            assertEquals(
                "a-token-b",
                it.headers.entries.first { header -> header.key.equals("X-Punctuation", true) }.value.single()
            )
        }
    }

    @Test
    fun multipartDefaultFilenameUsesResolvedLocalPath() = withServer { base, received, dir ->
        dir.resolve("payload.bin").writeText("contents")
        val request = ImportedRequest(
            "Upload", "POST", ImportValue("$base/upload"),
            variables = mapOf("upload" to "payload.bin"),
            body = ImportBody.Multipart(listOf(ImportPart("file", ImportValue("{{upload}}", true), file = true)))
        )
        runScript(ImportScriptRenderer.render(request, baseDirectory = dir.absolutePath), dir)
        assertTrue(received.single().body.contains("filename=\"payload.bin\""), received.single().body)
    }

    @Test
    fun multipartEnvironmentPathUsesTheRuntimeFilenameAndPreservesAnExplicitOverride() =
        withServer { base, received, dir ->
            val upload = dir.resolve("payload.bin").apply { writeText("contents") }
            val request = ImportedRequest(
                "Upload", "POST", ImportValue("$base/upload"), body = ImportBody.Multipart(
                    listOf(
                        ImportPart("default", ImportValue("{{upload}}", true), file = true),
                        ImportPart("named", ImportValue("{{upload}}", true), file = true, filename = "custom.bin")
                    )
                )
            )
            runScript(ImportScriptRenderer.render(request), dir, listOf("--env-param", "upload=${upload.absolutePath}"))
            val body = received.single().body
            assertTrue(Regex("name=\"default\";\\s*filename=\"payload.bin\"").containsMatchIn(body), body)
            assertTrue(Regex("name=\"named\";\\s*filename=\"custom.bin\"").containsMatchIn(body), body)
        }

    private fun runScript(source: String, directory: File, options: List<String> = emptyList()) {
        val script = directory.resolve("imported.kttp.kts").apply { writeText(source) }
        val output = directory.resolve("cli.log")
        val jar = File(requireNotNull(System.getProperty("kttp.test.cliJar")))
        val args = listOf(
            File(System.getProperty("java.home"), "bin/java").absolutePath, "-jar", jar.absolutePath,
            "--script", script.absolutePath, "--response-dir", directory.resolve("responses").absolutePath,
            "--no-compilation-cache", "--json-output"
        ) + options
        val process = ProcessBuilder(args).redirectErrorStream(true).redirectOutput(output).start()
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly(); error("CLI import test timed out")
        }
        assertEquals(0, process.exitValue(), source + "\n" + output.readText())
    }

    private fun withServer(action: (String, MutableList<Captured>, File) -> Unit) {
        val directory = Files.createTempDirectory("kttp-import-http").toFile()
        val requests = Collections.synchronizedList(mutableListOf<Captured>())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests += Captured(
                exchange.requestMethod,
                exchange.requestURI.path,
                exchange.requestURI.rawQuery,
                exchange.requestHeaders.mapValues { it.value.toList() },
                exchange.requestBody.readBytes().toString(Charsets.ISO_8859_1)
            )
            val bytes = "{}".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            action("http://127.0.0.1:${server.address.port}", requests, directory)
        } finally {
            server.stop(0); directory.deleteRecursively()
        }
    }

    /**
     * Captures wire values rather than reproducing the import implementation in assertions.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private data class Captured(
        val method: String,
        val path: String,
        val query: String?,
        val headers: Map<String, List<String>>,
        val body: String
    )
}
