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
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.sun.net.httpserver.HttpServer
import io.github.photowey.kttp.idea.cli.KttpCliInvocation
import io.github.photowey.kttp.idea.execution.ScriptRequestExecutor
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * Verifies script-backed GUI request selection, execution semantics, and process cancellation.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class ScriptRequestExecutorTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()

    fun testPreviewArrivesBeforeThenCompletesAndFinalFailurePreservesResponse() {
        val directory = Files.createTempDirectory("kttp-script-early-response")
        Files.createDirectories(java.nio.file.Path.of(requireNotNull(project.basePath)))
        val release = directory.resolve("release.txt")
        val script = directory.resolve("early.kttp.kts")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val bytes = "{\"early\":true}".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        installRuntimeSdk()
        val previous = System.getProperty("kttp.cli.jar")
        System.setProperty("kttp.cli.jar", System.getProperty("kttp.test.cliJar"))
        val cancellation = GuiRequestCancellation()
        val preview = java.util.concurrent.atomic.AtomicReference<io.github.photowey.kttp.idea.execution.KttpExecutionResult>()
        Files.writeString(script, """
            GET("http://127.0.0.1:${server.address.port}/").then {
                while (!java.io.File("${release.toString().replace("\\", "\\\\")}").exists()) Thread.sleep(50)
                error("after-preview")
            }
        """.trimIndent())
        val pool = java.util.concurrent.Executors.newSingleThreadExecutor()
        val future = pool.submit<io.github.photowey.kttp.idea.execution.KttpExecutionResult> {
            ScriptRequestExecutor.execute(project, KttpCliInvocation(script.toString(), requestNumber = 0,
                responseDir = directory.resolve("responses").toString()), cancellation) { preview.set(it) }
        }
        try {
            com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching("Response arrives while then is waiting", {
                preview.get()?.statusCode == 200
            }, 20)
            assertEquals("{\"early\":true}", preview.get().body)
            assertFalse("Provisional response cannot require script completion", future.isDone)
            Files.writeString(release, "finish")
            val result = future.get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertFalse(result.success)
            assertTrue(result.message, result.message.contains("after-preview"))
            assertEquals(200, result.statusCode)
            assertEquals("{\"early\":true}", result.body)
            assertTrue(java.io.File(requireNotNull(result.responseFilePath)).isFile)
        } finally {
            cancellation.cancel()
            future.cancel(true)
            pool.shutdownNow()
            if (previous == null) System.clearProperty("kttp.cli.jar") else System.setProperty("kttp.cli.jar", previous)
            server.stop(0)
            directory.toFile().deleteRecursively()
        }
    }

    fun testGuiScriptAdapterPreservesDynamicUrlClientConfigurationSelectionAndThen() {
        val directory = Files.createTempDirectory("kttp-runtime-parity")
        Files.createDirectories(java.nio.file.Path.of(requireNotNull(project.basePath)))
        val marker = directory.resolve("then.txt")
        val script = directory.resolve("parity.kttp.kts")
        val selectedHits = AtomicInteger()
        val unwantedHits = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/selected") { exchange ->
            selectedHits.incrementAndGet()
            exchange.responseHeaders.add("Location", "/unwanted")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/unwanted") { exchange ->
            unwantedHits.incrementAndGet()
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        installRuntimeSdk()
        val oldJar = System.getProperty("kttp.cli.jar")
        System.setProperty("kttp.cli.jar", System.getProperty("kttp.test.cliJar"))
        try {
            Files.writeString(
                script, """
                val ROOT: String by env
                fun selectedUrl() = ROOT + "/selected"
                configureClient {
                    addInterceptor { chain ->
                        chain.proceed(chain.request().newBuilder().header("X-Configured", "yes").build())
                    }
                }
                GET(ROOT + "/unwanted")
                GET(selectedUrl(), name = "selected") { noRedirect() }.then {
                    assertStatus(302)
                    java.io.File("${marker.toString().replace("\\", "\\\\")}").writeText("then-ran")
                }
            """.trimIndent()
            )
            val invocation = KttpCliInvocation(
                script.toString(),
                requestNumber = 1,
                envVariables = mapOf("ROOT" to "http://127.0.0.1:${server.address.port}"),
                jsonOutput = true,
                responseDir = directory.resolve("responses").toString(),
                baseDir = directory.toString()
            )
            val result = ScriptRequestExecutor.execute(project, invocation, GuiRequestCancellation())
            assertTrue(result.message, result.success)
            assertEquals(302, result.statusCode)
            assertEquals(
                io.github.photowey.kttp.idea.execution.RequestExecutionMode.FOLLOW_SCRIPT,
                result.executionMode
            )
            assertNotNull(result.totalTimeMs)
            assertNotNull(result.httpTimeMs)
            assertTrue(result.totalTimeMs!! >= result.httpTimeMs!!)
            assertEquals("yes", result.requestHeaders["X-Configured"])
            assertEquals("then-ran", Files.readString(marker))
            assertEquals(1, selectedHits.get())
            assertEquals(0, unwantedHits.get())
            assertTrue(java.io.File(requireNotNull(result.responseFilePath)).isFile)

            Files.writeString(script, Files.readString(script).replace("assertStatus(302)", "error(\"then-failure\")"))
            val failed = ScriptRequestExecutor.execute(project, invocation, GuiRequestCancellation())
            assertFalse(failed.success)
            assertFalse(failed.assertionsPassed)
            assertEquals(302, failed.statusCode)
            assertNotNull(failed.httpTimeMs)
            assertTrue(java.io.File(requireNotNull(failed.responseFilePath)).isFile)
            assertTrue(failed.message, failed.message.contains("then-failure"))
            assertEquals(2, selectedHits.get())
            assertEquals(0, unwantedHits.get())
        } finally {
            if (oldJar == null) System.clearProperty("kttp.cli.jar") else System.setProperty("kttp.cli.jar", oldJar)
            server.stop(0)
            directory.toFile().deleteRecursively()
        }
    }

    fun testCancelledBeforeStartDoesNotRequireJdkOrExecuteScript() {
        val cancellation = GuiRequestCancellation().apply { cancel() }
        val result = ScriptRequestExecutor.execute(
            project,
            KttpCliInvocation("/missing.kttp.kts", requestNumber = 0),
            cancellation
        )
        assertFalse(result.success)
        assertEquals("Request cancelled", result.message)
    }

    fun testMissingSelectionCannotExecuteWholeFile() {
        val result =
            ScriptRequestExecutor.execute(project, KttpCliInvocation("/missing.kttp.kts"), GuiRequestCancellation())
        assertFalse(result.success)
        assertTrue(result.message, result.message.contains("Select one script request"))
    }

    fun testCancellationTerminatesScriptProcessDuringThen() {
        val directory = Files.createTempDirectory("kttp-cancel-runtime")
        Files.createDirectories(java.nio.file.Path.of(requireNotNull(project.basePath)))
        val script = directory.resolve("cancel.kttp.kts")
        val pidFile = directory.resolve("pid.txt")
        val marker = directory.resolve("finished.txt")
        Files.writeString(
            script, """
            GET("http://127.0.0.1:1/")
            java.io.File("$pidFile").writeText(java.lang.ProcessHandle.current().pid().toString())
            Thread.sleep(30000)
            java.io.File("$marker").writeText("should not finish")
        """.trimIndent()
        )
        installRuntimeSdk()
        val oldJar = System.getProperty("kttp.cli.jar")
        System.setProperty("kttp.cli.jar", System.getProperty("kttp.test.cliJar"))
        val cancellation = GuiRequestCancellation()
        val pool = java.util.concurrent.Executors.newSingleThreadExecutor()
        val future = pool.submit<io.github.photowey.kttp.idea.execution.KttpExecutionResult> {
            ScriptRequestExecutor.execute(
                project,
                KttpCliInvocation(script.toString(), requestNumber = 0),
                cancellation
            )
        }
        try {
            com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching(
                "script started",
                { Files.exists(pidFile) },
                20
            )
            val process = ProcessHandle.of(Files.readString(pidFile).trim().toLong()).orElseThrow()
            cancellation.cancel()
            val result = future.get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertFalse(result.success)
            assertEquals("Request cancelled", result.message)
            process.onExit().get(3, java.util.concurrent.TimeUnit.SECONDS)
            assertFalse(process.isAlive)
            assertFalse(Files.exists(marker))
        } finally {
            cancellation.cancel()
            future.cancel(true)
            pool.shutdownNow()
            if (oldJar == null) System.clearProperty("kttp.cli.jar") else System.setProperty("kttp.cli.jar", oldJar)
            directory.toFile().deleteRecursively()
        }
    }

    private fun installRuntimeSdk() {
        ApplicationManager.getApplication().runWriteAction {
            val sdk = JavaSdk.getInstance().createJdk("Kttp experiment JDK", System.getProperty("java.home"), false)
            ProjectJdkTable.getInstance().addJdk(sdk, testRootDisposable)
            ProjectRootManager.getInstance(project).projectSdk = sdk
        }
    }
}
