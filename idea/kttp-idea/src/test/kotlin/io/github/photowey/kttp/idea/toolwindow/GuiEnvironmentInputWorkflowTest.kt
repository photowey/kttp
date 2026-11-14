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

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sun.net.httpserver.HttpServer
import io.github.photowey.kttp.core.enums.HttpMethod
import io.github.photowey.kttp.idea.gui.*
import java.awt.Component
import java.awt.Container
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JButton
import javax.swing.JLabel

/**
 * Verifies GUI environment input through persistence, script generation and direct HTTP execution.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/14
 */
class GuiEnvironmentInputWorkflowTest : BasePlatformTestCase() {
    fun testSimpleAndLegacyInputsSurviveSaveReopenGenerateAndExecute() {
        val service = project.getService(GuiDraftService::class.java)
        val draft = service.createEmptyDraft().copy(id = UUID.randomUUID().toString())
        val editor = RequestEditorPanel(project) { _, _ -> }
        editor.loadDraft(draft)
        val header = descendants(editor).filterIsInstance<ComposerHeaderPanel>().single()
        header.methodComboBox.selectedItem = HttpMethod.POST.name
        header.urlField.text = "\${baseUrl}"
        descendants(editor).filterIsInstance<ParamsTabPanel>().single().setParamEntries(
            listOf("q" to "\${QUERY}", "literal" to "\\\${QUERY}")
        )
        descendants(editor).filterIsInstance<HeadersTabPanel>().single().setHeaderEntries(
            listOf("X-Token" to "\${TOKEN}", "X-Legacy" to "\${env:TOKEN}", "X-Literal" to "\\\${TOKEN}")
        )
        descendants(editor).filterIsInstance<AuthTabPanel>().single().setAuth("bearer", mapOf("token" to "\${TOKEN}"))
        descendants(editor).filterIsInstance<BodyTabPanel>().single().setBody("raw", "\${BODY}")
        var generated: File? = null
        try {
            click(editor, "Save Draft")
            val saved = requireNotNull(GuiDraftService(project).findDraft(draft.id))
            assertEquals(ValueRef.Env("baseUrl"), saved.request.target.url)
            assertEquals(
                listOf(ValueRef.Env("TOKEN"), ValueRef.Env("TOKEN"), ValueRef.Literal("\${TOKEN}")),
                saved.request.headers.map { it.value })
            assertEquals(AuthSpec.Bearer(ValueRef.Env("TOKEN")), saved.request.auth)
            val reopened = RequestEditorPanel(project) { _, _ -> }
            reopened.loadDraft(saved)
            assertEquals(
                "\${baseUrl}",
                descendants(reopened).filterIsInstance<ComposerHeaderPanel>().single().urlField.text
            )
            assertEquals(
                listOf("\${TOKEN}", "\${TOKEN}", "\\\${TOKEN}"),
                descendants(reopened).filterIsInstance<HeadersTabPanel>().single().getHeaderEntries().map { it.second })
            click(reopened, "Generate Script")
            generated = File(requireNotNull(service.findDraft(draft.id)!!.bridge.generatedScriptPath))
            assertTrue(generated.readText().contains("by env"))
            assertFalse(generated.readText().contains("env:TOKEN"))
            assertRequestResolves(saved)
        } finally {
            generated?.delete()
            service.deleteDraft(draft.id)
        }
    }

    fun testSourceSummariesUseParsedReferencesAndRespectLiteralEscapes() {
        val entries = listOf(
            "a" to "\${TOKEN}", "b" to "\${env:TOKEN}", "c" to "\\\${TOKEN}",
            "d" to "Bearer \${TOKEN}", "e" to "\${env:broken"
        )
        val params = ParamsTabPanel().apply { setParamEntries(entries) }
        val headers = HeadersTabPanel().apply { setHeaderEntries(entries) }
        assertEquals("Sources: 2 env reference(s)", summary(params))
        assertEquals("Sources: 2 env reference(s)", summary(headers))
        val body = BodyTabPanel()
        body.setBody("raw", "\${BODY}")
        assertEquals("Sources: env reference", summary(body))
        body.setBody("raw", "\\\${BODY}")
        assertEquals("Sources: literal content", summary(body))
        val auth = AuthTabPanel()
        auth.setAuth("bearer", mapOf("token" to "\${TOKEN}"))
        assertEquals("Sources: token=env", summary(auth))
        auth.setAuth("bearer", mapOf("token" to "\\\${TOKEN}"))
        assertEquals("Sources: token=literal", summary(auth))
        auth.setAuth("bearer", mapOf("token" to "@file:literal.txt"))
        assertEquals("Sources: token=literal", summary(auth))
    }

    private fun assertRequestResolves(saved: GuiDraft) {
        val received = AtomicReference<List<String>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/echo") { exchange ->
            received.set(
                listOf(
                    exchange.requestMethod, exchange.requestURI.rawQuery,
                    exchange.requestHeaders.getFirst("X-Token"), exchange.requestHeaders.getFirst("X-Legacy"),
                    exchange.requestHeaders.getFirst("X-Literal"), exchange.requestHeaders.getFirst("Authorization"),
                    exchange.requestBody.bufferedReader().readText()
                )
            )
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.use { it.write("ok".toByteArray()) }
        }
        server.start()
        val responses = Files.createTempDirectory("kttp-gui-env-response")
        try {
            val values = mapOf(
                "baseUrl" to "http://127.0.0.1:${server.address.port}/echo",
                "TOKEN" to "resolved-token", "QUERY" to "resolved-query", "BODY" to "resolved-body"
            )
            val result = GuiRequestExecutor.execute(saved, values, null, responses)
            assertTrue(result.message, result.success)
            assertEquals(200, result.statusCode)
            assertEquals(
                listOf(
                    "POST", "q=resolved-query&literal=%24%7BQUERY%7D", "resolved-token", "resolved-token",
                    "\${TOKEN}", "Bearer resolved-token", "resolved-body"
                ), received.get()
            )
        } finally {
            server.stop(0)
            responses.toFile().deleteRecursively()
        }
    }

    private fun summary(panel: Component): String = descendants(panel).filterIsInstance<JLabel>()
        .single { it.text.orEmpty().startsWith("Sources:") }.text

    private fun click(panel: Component, text: String) {
        descendants(panel).filterIsInstance<JButton>().single { it.text == text }.doClick()
        com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
    }

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
    }
}
