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

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.AsyncProcessIcon
import com.sun.net.httpserver.HttpServer
import io.github.photowey.kttp.idea.execution.KttpExecutionResult
import io.github.photowey.kttp.idea.execution.RequestExecutionMode
import io.github.photowey.kttp.idea.gui.*
import io.github.photowey.kttp.idea.viewer.ResponseViewer
import java.awt.Component
import java.awt.Container
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JSplitPane

/**
 * Verifies immediate request feedback, cancellation cleanup and compact response metadata/actions.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/07
 */
class RequestFeedbackLayoutTest : BasePlatformTestCase() {
    fun testPreparationFailureRestoresSendAndStopsProgress() {
        val response = AtomicReference<KttpExecutionResult>()
        val editor = RequestEditorPanel(project) { result, _ -> response.set(result) }
        editor.loadDraft(GuiDraft(UUID.randomUUID().toString(), "Preparation failure", DraftOwnership.DETACHED,
            RequestSpec(target = RequestTarget(ValueRef.Literal("http://127.0.0.1:1/"))),
            executionMode = RequestExecutionMode.FOLLOW_SCRIPT))
        editor.onScriptFileChanged = { error("preparation-stopped") }
        val header = descendants(editor).filterIsInstance<ComposerHeaderPanel>().single()
        header.sendButton.doClick()
        assertEquals("preparation-stopped", response.get().message)
        assertEquals("Send", header.sendButton.text)
        assertTrue(header.sendButton.isEnabled)
        assertFalse(header.requestProgress.isVisible)
    }

    fun testProgressDisposalReleasesAnimationAndTimerFeedback() {
        val progress = RequestProgressPanel()
        progress.start(RequestExecutionMode.FOLLOW_SCRIPT)
        val spinner = descendants(progress).filterIsInstance<AsyncProcessIcon>().single()
        assertTrue(progress.isVisible)
        assertTrue(descendants(progress).filterIsInstance<JLabel>().single().text.contains("Running script"))
        progress.dispose()
        assertTrue(spinner.isDisposed)
        assertFalse(progress.isVisible)
    }

    fun testProvisionalResponseShowsPendingExecutionWithoutAddingHistory() {
        val response = ResponseTabPanel(project)
        val historyBefore = io.github.photowey.kttp.idea.io.HistoryPersistence.load()
        val main = MainContentPanel(project, response, HistoryTabPanel(project), AssertionsTabPanel(project))
        val editor = descendants(main).filterIsInstance<RequestEditorPanel>().single()
        editor.onResponsePreview!!.invoke(KttpExecutionResult("early", true, "OK", statusCode = 200, body = "{}"))
        val labels = descendants(response).filterIsInstance<JLabel>().map { it.text.orEmpty() }.toList()
        assertTrue(labels.contains("HTTP 200"))
        assertTrue(labels.any { it.contains("Execution in progress") })
        assertEquals(historyBefore, io.github.photowey.kttp.idea.io.HistoryPersistence.load())
        assertTrue(descendants(response).filterIsInstance<JButton>().all { !it.isEnabled })
    }
    fun testSendingShowsAnimationAndElapsedTimeThenStopsAfterCancellation() {
        val release = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            release.await(10, TimeUnit.SECONDS)
            exchange.close()
        }
        server.start()
        val result = AtomicReference<KttpExecutionResult>()
        val editor = RequestEditorPanel(project) { response, _ -> result.set(response) }
        editor.loadDraft(GuiDraft(UUID.randomUUID().toString(), "Feedback test", DraftOwnership.DETACHED,
            RequestSpec(target = RequestTarget(ValueRef.Literal("http://127.0.0.1:${server.address.port}/")))))
        try {
            val header = descendants(editor).filterIsInstance<ComposerHeaderPanel>().single()
            header.sendButton.doClick()
            val spinner = descendants(header).filterIsInstance<AsyncProcessIcon>().single()
            assertTrue("Animation must be visible immediately after Send", spinner.isVisible)
            val progress = descendants(header).filterIsInstance<JLabel>().single {
                it.accessibleContext.accessibleName == "Request progress"
            }
            val initial = progress.text
            PlatformTestUtil.waitWithEventsDispatching("Elapsed wait time updates", { progress.text != initial }, 3)
            assertTrue(progress.text.contains("Sending request"))
            assertTrue(header.sendButton.isEnabled)
            header.sendButton.doClick()
            assertFalse("A pending cancellation cannot send again", header.sendButton.isEnabled)
            assertTrue(progress.text.contains("Cancelling"))
            PlatformTestUtil.waitWithEventsDispatching("Cancellation completes", { result.get() != null }, 5)
            assertFalse(spinner.isVisible)
            assertFalse(progress.isVisible)
            assertEquals("Send", header.sendButton.text)
        } finally {
            release.countDown()
            server.stop(0)
        }
    }

    fun testResponseStatusIsNotResizableAndFileActionsAreCompactAndAccessible() {
        val viewer = ResponseViewer(project)
        viewer.setResponse(200, emptyMap(), "{}", true, emptyList(), null)
        assertTrue("Status must not use a separate vertical splitter", descendants(viewer).none { it is JSplitPane })
        val buttons = descendants(viewer).filterIsInstance<JButton>().toList()
        assertEquals(3, buttons.size)
        assertEquals(setOf("Open in Editor", "Copy Path", "Reveal in File Manager"),
            buttons.map { it.accessibleContext.accessibleName }.toSet())
        buttons.forEach {
            assertTrue("File actions use icons", it.text.isNullOrEmpty() && it.icon != null)
            assertFalse("No file means no actionable file buttons", it.isEnabled)
            assertNotNull(it.toolTipText)
        }
    }

    fun testResponseHasOneHeaderForStatusTimingAndExecutionFailure() {
        val response = ResponseTabPanel(project)
        response.showResponse(200, emptyMap(), "{}", responseFilePath = null,
            executionMode = RequestExecutionMode.FOLLOW_SCRIPT, totalTimeMs = 15290, httpTimeMs = 469,
            executionFailure = "Assertion failed")
        val viewer = descendants(response).filterIsInstance<ResponseViewer>().single()
        val labels = descendants(viewer).filterIsInstance<JLabel>().filter { it.isVisible }.map { it.text }.toList()
        assertTrue(labels.contains("HTTP 200"))
        assertTrue("Timing belongs with response metadata", labels.any { it?.contains("Total 15290 ms") == true })
        assertTrue(labels.any { it?.contains("Assertion failed") == true })
    }

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
    }
}
