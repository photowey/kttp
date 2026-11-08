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

package io.github.photowey.kttp.idea.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.sun.net.httpserver.HttpServer
import io.github.photowey.kttp.idea.execution.KttpExecutionResult
import io.github.photowey.kttp.idea.execution.RequestExecutionMode
import io.github.photowey.kttp.idea.gui.*
import io.github.photowey.kttp.idea.script.toKttpScript
import org.jetbrains.kotlin.psi.KtFile
import java.awt.Component
import java.awt.Container
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JButton

/**
 * Verifies the semantic differences and request selection behavior of both GUI execution modes.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class DualExecutionWorkflowTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()
    private var previousJar: String? = null
    private lateinit var directory: Path
    private lateinit var server: HttpServer
    private val received = Collections.synchronizedList(mutableListOf<Pair<String, String?>>())
    private val results = mutableListOf<Pair<KttpExecutionResult, String?>>()
    private val baseUrl get() = "http://127.0.0.1:${server.address.port}"

    override fun setUp() {
        super.setUp()
        Files.createDirectories(Path.of(requireNotNull(project.basePath)))
        directory = Files.createTempDirectory("kttp-dual-workflow")
        ApplicationManager.getApplication().runWriteAction {
            val sdk = JavaSdk.getInstance().createJdk("Dual workflow JDK", System.getProperty("java.home"), false)
            ProjectJdkTable.getInstance().addJdk(sdk, testRootDisposable)
            ProjectRootManager.getInstance(project).projectSdk = sdk
        }
        previousJar = System.getProperty("kttp.cli.jar")
        System.setProperty("kttp.cli.jar", System.getProperty("kttp.test.cliJar"))
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            received.add(exchange.requestURI.path to exchange.requestHeaders.getFirst("X-Script"))
            exchange.requestBody.readAllBytes()
            exchange.responseHeaders.add("Content-Type", "application/json")
            val body = "{\"ok\":true}".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }

    override fun tearDown() {
        try {
            server.stop(0)
            results.mapNotNull { it.first.responseFilePath }.forEach { File(it).delete() }
            directory.toFile().deleteRecursively()
            if (previousJar == null) System.clearProperty("kttp.cli.jar") else System.setProperty(
                "kttp.cli.jar",
                previousJar
            )
        } finally {
            super.tearDown()
        }
    }

    fun testFollowPreservesThenWhileQuickSkipsIt() {
        val marker = directory.resolve("then.txt")
        val editor = editor()
        loadSource(
            editor, """
            GET("$baseUrl/selected").then {
                java.io.File("$marker").appendText("ran")
            }
        """.trimIndent()
        )
        val first = send(editor)
        assertTrue(first.message, first.success)
        assertEquals(RequestExecutionMode.FOLLOW_SCRIPT, first.executionMode)
        assertEquals("ran", Files.readString(marker))
        header(editor).executionModeCombo.selectedItem = RequestExecutionMode.QUICK_REQUEST
        val second = send(editor)
        assertTrue(second.message, second.success)
        assertEquals(RequestExecutionMode.QUICK_REQUEST, second.executionMode)
        assertEquals("ran", Files.readString(marker))
        assertEquals(listOf("/selected", "/selected"), received.map { it.first })
        assertNotNull(first.httpTimeMs)
        assertTrue(first.totalTimeMs!! >= first.httpTimeMs!!)
        assertNotNull(second.totalTimeMs)
    }

    fun testFollowRunsDynamicSelectedRequestWithGlobalConfiguration() {
        val editor = editor()
        loadSource(
            editor, """
            fun target() = "$baseUrl/selected"
            configureClient {
                addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("X-Script", "configured").build())
                }
            }
            GET("$baseUrl/unwanted")
            GET(target())
        """.trimIndent(), 1
        )
        val result = send(editor)
        assertTrue(result.message, result.success)
        assertEquals(listOf("/selected" to "configured"), received.toList())
        assertTrue(File(result.responseFilePath!!).isFile)
        assertEquals(editor.currentSourceFile!!.path, results.single().second)
    }

    fun testHelperRegistrationsCannotShiftTheSelectedRequest() {
        val editor = editor()
        loadSource(
            editor, """
            fun extra() = GET("$baseUrl/unwanted")
            extra()
            GET("$baseUrl/selected")
        """.trimIndent()
        )
        val result = send(editor)
        assertTrue(result.message, result.success)
        assertEquals(listOf("/selected"), received.map { it.first })
    }

    fun testUnregisteredConditionalCannotShiftTheSelectedRequest() {
        val editor = editor()
        loadSource(
            editor, """
            if (false) { GET("$baseUrl/unwanted") }
            GET(
                "$baseUrl/selected"
            )
        """.trimIndent(), 1
        )
        val result = send(editor)
        assertTrue(result.message, result.success)
        assertEquals(listOf("/selected"), received.map { it.first })
    }

    fun testUnregisteredSelectionCannotExecuteTheNextRequest() {
        val editor = editor()
        loadSource(
            editor, """
            if (false) { GET("$baseUrl/absent") }
            GET("$baseUrl/unwanted")
        """.trimIndent()
        )
        val result = send(editor)
        assertFalse(result.success)
        assertTrue(result.message, result.message.contains("No registration"))
        assertTrue(received.isEmpty())
    }

    fun testRepeatedCallSiteCannotSilentlyExecuteOneIteration() {
        val editor = editor()
        loadSource(
            editor, """
            for (i in 0..1) { GET("$baseUrl/ambiguous") }
        """.trimIndent()
        )
        val result = send(editor)
        assertFalse(result.success)
        assertTrue(result.message, result.message.contains("Ambiguous"))
        assertTrue(received.isEmpty())
    }

    fun testSameLineDeclarationsFailClosedEvenWhenOneIsNotRegistered() {
        val editor = editor()
        loadSource(editor, "if (false) { GET(\"$baseUrl/absent\") }; GET(\"$baseUrl/unwanted\")")
        val result = send(editor)
        assertFalse(result.success)
        assertTrue(result.message, result.message.contains("Ambiguous"))
        assertTrue(received.isEmpty())
    }

    fun testFollowSavesLatestUnsavedScriptBeforeLaunchingCli() {
        val editor = editor()
        val file = loadSource(editor, "GET(\"$baseUrl/original\")")
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile)!!
        WriteCommandAction.runWriteCommandAction(project) {
            val start = document.text.indexOf("/original")
            document.replaceString(start, start + "/original".length, "/updated")
        }
        val result = send(editor)
        assertTrue(result.message, result.success)
        assertEquals(listOf("/updated"), received.map { it.first })
        assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document))
    }

    fun testGuiDraftFollowSynchronizesTheCurrentFormAndKeepsSourceAssociation() {
        val service = project.getService(GuiDraftService::class.java)
        val draft = service.createEmptyDraft()
            .copy(request = RequestSpec(target = RequestTarget(ValueRef.Literal("$baseUrl/first"))))
        val editor = editor()
        editor.loadDraft(draft)
        header(editor).executionModeCombo.selectedItem = RequestExecutionMode.FOLLOW_SCRIPT
        var generated: File? = null
        try {
            assertTrue(send(editor).success)
            generated = File(results.last().second!!)
            header(editor).urlField.text = "$baseUrl/current"
            val result = send(editor)
            assertTrue(result.message, result.success)
            assertEquals(listOf("/first", "/current"), received.map { it.first })
            assertEquals(generated.path, results.last().second)
            assertTrue(generated.readText().contains("/current"))
            assertEquals(RequestExecutionMode.FOLLOW_SCRIPT, service.findDraft(draft.id)!!.executionMode)
        } finally {
            generated?.delete()
            service.deleteDraft(draft.id)
        }
    }

    fun testCancellationRecoversButtonAndCannotOverwriteAnotherDraft() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.createContext("/slow") { exchange ->
            entered.countDown()
            release.await(20, TimeUnit.SECONDS)
            runCatching { exchange.sendResponseHeaders(200, -1); exchange.close() }
        }
        val editor = editor()
        loadSource(editor, "GET(\"$baseUrl/slow\")")
        try {
            val start = System.nanoTime()
            button(editor, "Send").doClick()
            assertTrue(
                "Send cannot wait on the script runtime on EDT",
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1000
            )
            assertFalse(header(editor).executionModeCombo.isEnabled)
            PlatformTestUtil.waitWithEventsDispatching("HTTP started", { entered.count == 0L }, 20)
            button(editor, "Cancel").doClick()
            val next = project.getService(GuiDraftService::class.java).createEmptyDraft().copy(title = "Next request")
            editor.loadDraft(next)
            PlatformTestUtil.waitWithEventsDispatching("cancel completion", { results.isNotEmpty() }, 10)
            assertFalse(results.single().first.success)
            assertEquals("Request cancelled", results.single().first.message)
            assertEquals("Next request", header(editor).titleField.text)
            assertEquals(RequestExecutionMode.QUICK_REQUEST, header(editor).executionModeCombo.selectedItem)
            assertTrue(header(editor).executionModeCombo.isEnabled)
            assertNotNull(button(editor, "Send"))
        } finally {
            release.countDown()
        }
    }

    fun testDisposingEditorTerminatesScriptWithoutPublishingLateResult() {
        val pidFile = directory.resolve("dispose-pid.txt")
        val finished = directory.resolve("dispose-finished.txt")
        val editor = editor()
        loadSource(
            editor, """
            GET("$baseUrl/dispose")
            java.io.File("$pidFile").writeText(java.lang.ProcessHandle.current().pid().toString())
            Thread.sleep(30000)
            java.io.File("$finished").writeText("must not finish")
        """.trimIndent()
        )
        button(editor, "Send").doClick(0)
        try {
            PlatformTestUtil.waitWithEventsDispatching("dispose script started", { Files.exists(pidFile) }, 20)
            val process = ProcessHandle.of(Files.readString(pidFile).trim().toLong()).orElseThrow()
            editor.dispose()
            PlatformTestUtil.waitWithEventsDispatching("disposed script stopped", { !process.isAlive }, 10)
            assertTrue(results.isEmpty())
            assertTrue(received.isEmpty())
            assertFalse(Files.exists(finished))
        } finally {
            editor.dispose()
        }
    }

    fun testMeasureBothModesIncludingUrlHeadersAndJsonEdits() {
        val service = project.getService(GuiDraftService::class.java)
        val draft = service.createEmptyDraft().copy(
            title = "Latency ${System.nanoTime()}", request = RequestSpec(
                method = io.github.photowey.kttp.core.enums.HttpMethod.POST,
                target = RequestTarget(ValueRef.Literal("$baseUrl/benchmark")),
                headers = listOf(HeaderSpec("X-Case", ValueRef.Literal("original"))),
                body = BodySpec.Json(ValueRef.Literal("{\"value\":1}"))
            )
        )
        val editor = editor()
        editor.loadDraft(draft)
        val samples = mutableListOf("scenario,mode,total_ms,http_ms")
        var generated: File? = null
        fun measure(scenario: String): KttpExecutionResult {
            val before = results.size
            button(editor, "Send").doClick(0)
            PlatformTestUtil.waitWithEventsDispatching("latency result", { results.size > before }, 30)
            val result = results.last().first
            assertTrue(result.message, result.success)
            assertEquals(200, result.statusCode)
            assertNotNull(result.httpTimeMs)
            assertNotNull(result.totalTimeMs)
            assertTrue(File(result.responseFilePath!!).isFile)
            samples.add("$scenario,${result.executionMode!!.name},${result.totalTimeMs},${result.httpTimeMs}")
            return result
        }
        try {
            measure("quick_first")
            repeat(5) { measure("quick_repeat") }
            header(editor).executionModeCombo.selectedItem = RequestExecutionMode.FOLLOW_SCRIPT
            measure("script_first")
            generated = File(results.last().second!!)
            repeat(3) { measure("script_repeat") }
            header(editor).urlField.text = "$baseUrl/edited-url"
            assertEquals("$baseUrl/edited-url", measure("url_edited").requestUrl)
            measure("url_repeat")
            descendants(editor).filterIsInstance<HeadersTabPanel>().single()
                .setHeaderEntries(listOf("X-Case" to "changed"))
            assertEquals("changed", measure("headers_edited").requestHeaders["X-Case"])
            measure("headers_repeat")
            descendants(editor).filterIsInstance<BodyTabPanel>().single()
                .setBodySpec(BodySpec.Json(ValueRef.Literal("{\"value\":2}")))
            val bodyResult = measure("json_edited")
            assertEquals(
                kotlinx.serialization.json.Json.parseToJsonElement("{\"value\":2}"),
                kotlinx.serialization.json.Json.parseToJsonElement(requireNotNull(bodyResult.requestBody))
            )
            measure("json_repeat")
            header(editor).executionModeCombo.selectedItem = RequestExecutionMode.QUICK_REQUEST
            assertEquals("$baseUrl/edited-url", measure("quick_after_edits").requestUrl)
            assertEquals(samples.size - 1, received.size)
        } finally {
            val report = Path.of(System.getProperty("user.dir"), "build/reports/dual-execution")
            Files.createDirectories(report)
            Files.writeString(report.resolve("end-to-end.csv"), samples.joinToString("\n") + "\n")
            generated?.delete()
            service.deleteDraft(draft.id)
        }
    }

    private fun editor() = RequestEditorPanel(project) { result, path -> results.add(result to path) }

    private fun loadSource(editor: RequestEditorPanel, text: String, index: Int = 0): KtFile {
        val file = myFixture.addFileToProject("request-${System.nanoTime()}.kttp.kts", text) as KtFile
        FileDocumentManager.getInstance()
            .saveDocument(FileDocumentManager.getInstance().getDocument(file.virtualFile)!!)
        editor.loadFromRequest(file.toKttpScript()!!.findRequestDeclarations()[index], file.virtualFile, index)
        return file
    }

    private fun send(editor: RequestEditorPanel): KttpExecutionResult {
        val before = results.size
        assertTrue("Send should be enabled: ${header(editor).sendButton.toolTipText}", button(editor, "Send").isEnabled)
        button(editor, "Send").doClick()
        PlatformTestUtil.waitWithEventsDispatching("request result", { results.size > before }, 30)
        return results.last().first
    }

    private fun header(editor: RequestEditorPanel) =
        descendants(editor).filterIsInstance<ComposerHeaderPanel>().single()

    private fun button(editor: RequestEditorPanel, text: String) =
        descendants(editor).filterIsInstance<JButton>().single { it.text == text }

    private fun descendants(component: Component): List<Component> = listOf(component) +
        if (component is Container) component.components.flatMap(::descendants) else emptyList()
}
