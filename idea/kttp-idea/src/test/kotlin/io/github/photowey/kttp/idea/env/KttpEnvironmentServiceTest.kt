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

package io.github.photowey.kttp.idea.env

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.sun.net.httpserver.HttpServer
import io.github.photowey.kttp.core.enums.HttpMethod
import io.github.photowey.kttp.idea.execution.KttpExecutionResult
import io.github.photowey.kttp.idea.gui.BodySpec
import io.github.photowey.kttp.idea.gui.DraftOwnership
import io.github.photowey.kttp.idea.gui.GuiDraft
import io.github.photowey.kttp.idea.gui.RequestSpec
import io.github.photowey.kttp.idea.gui.RequestTarget
import io.github.photowey.kttp.idea.gui.ValueRef
import io.github.photowey.kttp.idea.path.KttpConsolesPath
import io.github.photowey.kttp.idea.run.KttpRunConfiguration
import io.github.photowey.kttp.idea.run.KttpRunConfigurationType
import io.github.photowey.kttp.idea.run.KttpRunProfileState
import io.github.photowey.kttp.idea.toolwindow.RequestEditorPanel
import java.awt.Component
import java.awt.Container
import java.io.File
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JButton

/**
 * Verifies script-local and console environment lookup and selection behavior.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class KttpEnvironmentServiceTest : BasePlatformTestCase() {
    private lateinit var consoleDirectory: VirtualFile
    private var originalConsoleEnvironment: ByteArray? = null
    private val environments get() = KttpEnvironmentService.getInstance(project)

    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()

    override fun setUp() {
        super.setUp()
        consoleDirectory = requireNotNull(
            LocalFileSystem.getInstance()
                .refreshAndFindFileByIoFile(KttpConsolesPath.getKttpConsolesDir())
        )
        val existing = consoleDirectory.findChild(KttpEnvFileType.FILE_NAME)
        originalConsoleEnvironment = existing?.contentsToByteArray()
        WriteCommandAction.runWriteCommandAction(project) { existing?.delete(this) }
        environments.setSelectedEnvironment(null)
    }

    override fun tearDown() {
        try {
            environments.setSelectedEnvironment(null)
            WriteCommandAction.runWriteCommandAction(project) {
                consoleDirectory.findChild(KttpEnvFileType.FILE_NAME)?.delete(this)
                originalConsoleEnvironment?.let { bytes ->
                    consoleDirectory.createChildData(this, KttpEnvFileType.FILE_NAME).setBinaryContent(bytes)
                }
            }
        } finally {
            super.tearDown()
        }
    }

    fun testScriptParentEnvironmentTakesPriorityOverConsoleEnvironment() {
        consoleEnvironment("""{"dev":{"origin":"console","consoleOnly":"global"}}""")
        val script = script("requests/orders.kttp.kts")
        val local = myFixture.tempDirFixture.createFile(
            "requests/kttp.env.json", """{"dev":{"origin":"local"}}"""
        )
        environments.setSelectedEnvironment("dev")

        assertEquals(local, environments.findEnvFile(script))
        assertEquals(mapOf("origin" to "local"), environments.getEnvironmentVariables(script))
    }

    fun testNestedScriptFallsBackToConsoleRootEnvironment() {
        val global = consoleEnvironment("""{"dev":{"origin":"console"}}""")
        var script: VirtualFile? = null
        WriteCommandAction.runWriteCommandAction(project) {
            val requests = consoleDirectory.createChildDirectory(this, "env-test-${UUID.randomUUID()}")
            script = requests.createChildData(this, "orders.kttp.kts")
        }
        try {
            environments.setSelectedEnvironment("dev")
            assertEquals(global, environments.findEnvFile(script))
            assertEquals(mapOf("origin" to "console"), environments.getEnvironmentVariables(script))
        } finally {
            WriteCommandAction.runWriteCommandAction(project) { requireNotNull(script).parent.delete(this) }
        }
    }

    fun testConsoleEnvironmentIsAvailableWithoutAScriptFile() {
        val global = consoleEnvironment("""{"dev":{"origin":"console"}}""")
        environments.setSelectedEnvironment("dev")

        assertEquals(global, environments.findEnvFile())
        assertEquals(mapOf("origin" to "console"), environments.getEnvironmentVariables())
    }

    fun testJsonStringsDecodeQuotesBackslashesNewlinesAndUnicode() {
        val script = script("decoded/request.kttp.kts")
        myFixture.tempDirFixture.createFile(
            "decoded/kttp.env.json", """
            {"dev":{
              "quoted":"\"quoted\"",
              "path":"C:\\temp\\data",
              "lines":"first\nsecond",
              "unicode":"\u4F60\u597D \uD83D\uDE03",
              "empty":""
            }}
        """.trimIndent()
        )
        environments.setSelectedEnvironment("dev")

        assertEquals(
            mapOf(
                "quoted" to "\"quoted\"", "path" to "C:\\temp\\data",
                "lines" to "first\nsecond", "unicode" to "你好 😃", "empty" to ""
            ), environments.getEnvironmentVariables(script)
        )
    }

    fun testNonStringValuesKeepTheirJsonSourceText() {
        val script = script("scalars/request.kttp.kts")
        myFixture.tempDirFixture.createFile(
            "scalars/kttp.env.json", """
            {"dev":{
              "number":1.20e+3, "enabled":true, "disabled":false, "nothing":null,
              "object":{ "nested": "value" }, "array":[1, "two"]
            },"ignored":false}
        """.trimIndent()
        )
        environments.setSelectedEnvironment("dev")

        assertEquals(
            mapOf(
                "number" to "1.20e+3", "enabled" to "true", "disabled" to "false", "nothing" to "null",
                "object" to "{ \"nested\": \"value\" }", "array" to "[1, \"two\"]"
            ), environments.getEnvironmentVariables(script)
        )
        assertEquals(listOf("dev"), environments.getAvailableEnvironments(script).map { it.name })
    }

    fun testNoSelectedEnvironmentDoesNotInjectVariables() {
        consoleEnvironment("""{"dev":{"origin":"console"}}""")
        assertTrue(environments.getEnvironmentVariables().isEmpty())
        environments.setSelectedEnvironment("missing")
        assertTrue(environments.getEnvironmentVariables().isEmpty())
    }

    fun testRunAndGuiSendUseTheSameDecodedConsoleFallback() {
        consoleEnvironment("""{"dev":{"payload":"say \"hello\" at C:\\tmp\n\u4F60\u597D"}}""")
        val script = script("nested/workflow.kttp.kts")
        environments.setSelectedEnvironment("dev")
        val expected = "say \"hello\" at C:\\tmp\n你好"

        assertEquals(listOf("payload=$expected"), runEnvironmentArguments(script))
        assertEquals(expected, guiSentBody(script))
    }

    private fun script(path: String): VirtualFile =
        myFixture.tempDirFixture.createFile(path, "GET(\"https://example.com/\")\n")

    private fun consoleEnvironment(json: String): VirtualFile {
        var file: VirtualFile? = null
        WriteCommandAction.runWriteCommandAction(project) {
            file = consoleDirectory.createChildData(this, KttpEnvFileType.FILE_NAME)
            file!!.setBinaryContent(json.toByteArray(Charsets.UTF_8))
        }
        return requireNotNull(file)
    }

    private fun runEnvironmentArguments(script: VirtualFile): List<String> {
        ApplicationManager.getApplication().runWriteAction {
            val sdk = IdeaTestUtil.getMockJdk21()
            ProjectJdkTable.getInstance().addJdk(sdk, testRootDisposable)
            ProjectRootManager.getInstance(project).projectSdk = sdk
        }
        val configuration = KttpRunConfigurationType().createTemplateConfiguration(project) as KttpRunConfiguration
        configuration.scriptPath = script.path
        val environment = ExecutionEnvironmentBuilder.create(
            project, DefaultRunExecutor.getRunExecutorInstance(), configuration
        ).build()
        val originalCliJar = System.getProperty("kttp.cli.jar")
        try {
            System.setProperty("kttp.cli.jar", System.getProperty("kttp.test.cliJar"))
            val arguments = KttpRunProfileState(configuration, environment).javaParameters
                .programParametersList.parameters
            File(arguments[arguments.indexOf("--response-manifest") + 1]).delete()
            return arguments.withIndex().filter { it.value == "--env-param" }.map { arguments[it.index + 1] }
        } finally {
            if (originalCliJar == null) System.clearProperty("kttp.cli.jar")
            else System.setProperty("kttp.cli.jar", originalCliJar)
        }
    }

    private fun guiSentBody(script: VirtualFile): String? {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.readAllBytes()
            exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val result = AtomicReference<KttpExecutionResult>()
        val editor = RequestEditorPanel(project) { response, _ -> result.set(response) }
        try {
            val request = RequestSpec(
                method = HttpMethod.POST,
                target = RequestTarget(ValueRef.Literal("http://127.0.0.1:${server.address.port}/")),
                body = BodySpec.Raw(ValueRef.Env("payload"))
            )
            editor.loadDraft(GuiDraft("environment-workflow", "Environment workflow", DraftOwnership.DETACHED, request))
            editor.currentSourceFile = script
            descendants(editor).filterIsInstance<JButton>().single { it.text == "Send" }.doClick()
            PlatformTestUtil.waitWithEventsDispatching("GUI environment request", { result.get() != null }, 10)
            assertTrue(result.get().message, result.get().success)
            return result.get().body
        } finally {
            Disposer.dispose(editor)
            server.stop(0)
        }
    }

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
    }
}
