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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.sun.net.httpserver.HttpServer
import io.github.photowey.kttp.idea.cli.KttpCliInvocation
import io.github.photowey.kttp.idea.execution.KttpExecutionResult
import io.github.photowey.kttp.idea.execution.ScriptRequestExecutor
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Verifies recovery of script responses from execution checkpoints without attaching unrelated files.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/06
 */
class ScriptResponseRecoveryTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()

    fun testCancellationDuringThenRetainsCompletedHttpResponseAndFile() {
        val directory = Files.createTempDirectory("kttp-cancel-after-http")
        Files.createDirectories(Path.of(requireNotNull(project.basePath)))
        val marker = directory.resolve("then-started.txt")
        val script = directory.resolve("cancel-after-http.kttp.kts")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/completed") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("X-Recovery", "completed")
            val bytes = "{\"received\":true}".toByteArray()
            exchange.sendResponseHeaders(201, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        Files.writeString(
            script, """
            GET("http://127.0.0.1:${server.address.port}/completed").then {
                java.io.File("$marker").writeText(java.lang.ProcessHandle.current().pid().toString())
                Thread.sleep(30000)
            }
        """.trimIndent()
        )
        installRuntimeSdk()
        val oldJar = System.getProperty("kttp.cli.jar")
        System.setProperty("kttp.cli.jar", System.getProperty("kttp.test.cliJar"))
        val cancellation = GuiRequestCancellation()
        val pool = Executors.newSingleThreadExecutor()
        val future = pool.submit<KttpExecutionResult> {
            ScriptRequestExecutor.execute(
                project, KttpCliInvocation(
                    script.toString(), requestNumber = 0,
                    responseDir = directory.resolve("responses").toString()
                ), cancellation
            )
        }
        try {
            PlatformTestUtil.waitWithEventsDispatching("then started after HTTP", { Files.exists(marker) }, 30)
            val process = ProcessHandle.of(Files.readString(marker).trim().toLong()).orElseThrow()
            cancellation.cancel()
            val result = future.get(5, TimeUnit.SECONDS)
            process.onExit().get(3, TimeUnit.SECONDS)
            assertFalse(result.success)
            assertEquals("Request cancelled", result.message)
            assertEquals(201, result.statusCode)
            assertEquals("completed", result.headers.entries.first { it.key.equals("X-Recovery", true) }.value)
            assertTrue(result.body.orEmpty().contains("received"))
            assertTrue(File(requireNotNull(result.responseFilePath)).isFile)
            assertEquals("{\"received\":true}", File(result.responseFilePath!!).readText())
            assertNotNull(result.httpTimeMs)
            assertFalse(result.assertionsPassed)

            Files.writeString(
                script,
                Files.readString(script).replace("Thread.sleep(30000)", "kotlin.system.exitProcess(7)")
            )
            val exited = ScriptRequestExecutor.execute(
                project, KttpCliInvocation(
                    script.toString(), requestNumber = 0,
                    responseDir = directory.resolve("responses").toString()
                ), GuiRequestCancellation()
            )
            assertFalse(exited.success)
            assertFalse(exited.assertionsPassed)
            assertEquals(201, exited.statusCode)
            assertTrue(File(requireNotNull(exited.responseFilePath)).isFile)
            assertTrue(exited.body.orEmpty().contains("received"))
        } finally {
            cancellation.cancel()
            future.cancel(true)
            pool.shutdownNow()
            if (oldJar == null) System.clearProperty("kttp.cli.jar") else System.setProperty("kttp.cli.jar", oldJar)
            server.stop(0)
            directory.toFile().deleteRecursively()
        }
    }

    fun testCancellationDuringThenGivesEmptyResponseAHistoryFile() {
        val directory = Files.createTempDirectory("kttp-cancel-empty-response")
        Files.createDirectories(Path.of(requireNotNull(project.basePath)))
        val marker = directory.resolve("empty-then-started.txt")
        val responseDirectory = directory.resolve("responses")
        val script = directory.resolve("empty.kttp.kts")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/empty") { exchange ->
            exchange.responseHeaders.add("X-Recovery", "empty")
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        server.start()
        Files.writeString(
            script, """
            GET("http://127.0.0.1:${server.address.port}/empty").then {
                java.io.File("$marker").writeText(java.lang.ProcessHandle.current().pid().toString())
                Thread.sleep(30000)
            }
        """.trimIndent()
        )
        installRuntimeSdk()
        val oldJar = System.getProperty("kttp.cli.jar")
        System.setProperty("kttp.cli.jar", System.getProperty("kttp.test.cliJar"))
        val cancellation = GuiRequestCancellation()
        val pool = Executors.newSingleThreadExecutor()
        val future = pool.submit<KttpExecutionResult> {
            ScriptRequestExecutor.execute(
                project, KttpCliInvocation(
                    script.toString(), requestNumber = 0,
                    responseDir = responseDirectory.toString()
                ), cancellation
            )
        }
        try {
            PlatformTestUtil.waitWithEventsDispatching("empty response then started", { Files.exists(marker) }, 30)
            val process = ProcessHandle.of(Files.readString(marker).trim().toLong()).orElseThrow()
            cancellation.cancel()
            val result = future.get(5, TimeUnit.SECONDS)
            process.onExit().get(3, TimeUnit.SECONDS)
            assertFalse(result.success)
            assertEquals("Request cancelled", result.message)
            assertEquals(204, result.statusCode)
            assertEquals(0L, result.responseSize)
            assertEquals("empty", result.headers.entries.first { it.key.equals("X-Recovery", true) }.value)
            assertNotNull("Empty responses must have a file for History after cancellation", result.responseFilePath)
            val responseFile = File(requireNotNull(result.responseFilePath))
            assertTrue(responseFile.isFile)
            assertTrue(responseFile.canonicalFile.toPath().startsWith(responseDirectory.toRealPath()))
            assertEquals("Captured empty bodies remain empty after cancellation", 0L, responseFile.length())
            assertNotNull(result.httpTimeMs)
            assertFalse(result.assertionsPassed)
        } finally {
            cancellation.cancel()
            future.cancel(true)
            pool.shutdownNow()
            if (oldJar == null) System.clearProperty("kttp.cli.jar") else System.setProperty("kttp.cli.jar", oldJar)
            server.stop(0)
            directory.toFile().deleteRecursively()
        }
    }

    private fun installRuntimeSdk() {
        ApplicationManager.getApplication().runWriteAction {
            val sdk = JavaSdk.getInstance().createJdk("Kttp recovery JDK", System.getProperty("java.home"), false)
            ProjectJdkTable.getInstance().addJdk(sdk, testRootDisposable)
            ProjectRootManager.getInstance(project).projectSdk = sdk
        }
    }
}
