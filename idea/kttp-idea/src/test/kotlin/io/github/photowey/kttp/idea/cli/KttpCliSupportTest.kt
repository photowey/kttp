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

package io.github.photowey.kttp.idea.cli

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.nio.file.Files

/**
 * Verifies CLI argument construction, result parsing, and packaged or development JAR discovery.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class KttpCliSupportTest {

    @Test
    fun invocationBuildsStableProgramArguments() {
        val invocation = KttpCliInvocation(
            scriptPath = "/tmp/request.kttp.kts",
            requestNumber = 2,
            envVariables = linkedMapOf("Z_TOKEN" to "zzz", "A_HOST" to "http://localhost"),
            jsonOutput = true,
            responseDir = "/tmp/responses",
            baseDir = "/workspace/project",
            executionMode = "CURL"
        )

        assertEquals(
            listOf(
                "--script", "/tmp/request.kttp.kts",
                "--json-output",
                "--compilation-cache",
                "--response-dir", "/tmp/responses",
                "--base-dir", "/workspace/project",
                "--request-number", "2",
                "--execution-mode", "CURL",
                "--env-param", "A_HOST=http://localhost",
                "--env-param", "Z_TOKEN=zzz"
            ),
            invocation.toProgramArguments()
        )
    }

    @Test
    fun parserUsesLastJsonLineFromCliOutput() {
        val output = """
            compiling...
            {"success":true,"scriptName":"request.kttp.kts","message":"OK","statusCode":200,"protocol":"HTTP/2","headers":{"content-type":"application/json"},"body":"{\"ok\":true}","responseFilePath":"/tmp/response.json","requestMethod":"GET","requestUrl":"https://example.com"}
        """.trimIndent()

        val result = KttpCliJsonResultParser.parse("fallback.kttp.kts", output)

        assertTrue(result.success)
        assertEquals("request.kttp.kts", result.scriptName)
        assertEquals(200, result.statusCode)
        assertEquals("GET", result.requestMethod)
        assertEquals("https://example.com", result.requestUrl)
        assertEquals("/tmp/response.json", result.responseFilePath)
    }

    @Test
    fun parserFallsBackToErrorResultWhenOutputIsNotJson() {
        val result = KttpCliJsonResultParser.parse("broken.kttp.kts", "not json at all")

        assertFalse(result.success)
        assertTrue(result.message.startsWith("Failed to parse CLI output:"))
        assertEquals("not json at all", result.body)
    }

    @Test
    fun locatorFindsCliPackagedInPluginCliDirectory() {
        val pluginRoot = Files.createTempDirectory("kttp-plugin").toFile()
        val cliJar = pluginRoot.resolve("cli/kttp-cli.jar")
        cliJar.parentFile.mkdirs()
        cliJar.writeBytes(byteArrayOf(1))

        assertEquals(cliJar.canonicalFile, KttpCliLocator.findPackagedCliJar(pluginRoot)?.canonicalFile)
    }

    @Test
    fun locatorFindsVersionedShadowJarInDevelopmentBuildDirectory() {
        val projectRoot = Files.createTempDirectory("kttp-project").toFile()
        val libs = projectRoot.resolve("core/kttp-cli/build/libs")
        libs.mkdirs()
        val cliJar = libs.resolve("kttp-cli-1.0.0-SNAPSHOT.jar")
        cliJar.writeBytes(byteArrayOf(1))

        assertEquals(cliJar.canonicalFile, KttpCliLocator.findDevelopmentCliJar(projectRoot)?.canonicalFile)
    }

    @Test
    fun locatorSkipsClassifiedArchivesWhenSelectingDevelopmentCli() {
        val projectRoot = Files.createTempDirectory("kttp-classified-jars").toFile()
        try {
            val libs = projectRoot.resolve("core/kttp-cli/build/libs").apply { mkdirs() }
            listOf("plain", "sources", "javadoc").forEach { classifier ->
                libs.resolve("kttp-cli-0.1.0-$classifier.jar").writeBytes(byteArrayOf(1))
            }
            val cliJar = libs.resolve("kttp-cli-1.0.0.jar").apply { writeBytes(byteArrayOf(1)) }

            assertEquals(cliJar.canonicalFile, KttpCliLocator.findDevelopmentCliJar(projectRoot)?.canonicalFile)
        } finally {
            projectRoot.deleteRecursively()
        }
    }

    @Test
    fun locatorRejectsDevelopmentDirectoryWithOnlyClassifiedArchives() {
        val projectRoot = Files.createTempDirectory("kttp-classified-jars-only").toFile()
        try {
            val libs = projectRoot.resolve("core/kttp-cli/build/libs").apply { mkdirs() }
            listOf("plain", "sources", "javadoc").forEach { classifier ->
                libs.resolve("kttp-cli-1.0.0-$classifier.jar").writeBytes(byteArrayOf(1))
            }

            assertNull(KttpCliLocator.findDevelopmentCliJar(projectRoot))
        } finally {
            projectRoot.deleteRecursively()
        }
    }

    @Test
    fun locatorFindsDevelopmentCliFromRunIdePluginSandbox() {
        val projectRoot = Files.createTempDirectory("kttp-run-ide").toFile()
        val pluginRoot = projectRoot.resolve("idea/kttp-idea/build/idea-sandbox/IU/plugins/kttp-idea")
        pluginRoot.mkdirs()
        val libs = projectRoot.resolve("core/kttp-cli/build/libs")
        libs.mkdirs()
        val cliJar = libs.resolve("kttp-cli-1.0.0-SNAPSHOT.jar")
        cliJar.writeBytes(byteArrayOf(1))

        val previousPluginPath = System.getProperty("plugin.path")
        try {
            System.setProperty("plugin.path", pluginRoot.absolutePath)
            assertEquals(cliJar.canonicalFile, KttpCliLocator.findCliJar()?.canonicalFile)
        } finally {
            if (previousPluginPath == null) {
                System.clearProperty("plugin.path")
            } else {
                System.setProperty("plugin.path", previousPluginPath)
            }
        }
    }
}
