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
import io.github.photowey.kttp.idea.viewer.ResponseViewer
import java.awt.Component
import java.awt.Container
import javax.swing.JTabbedPane
import javax.swing.JTextArea

/**
 * Verifies assertion presentation distinguishes reported assertions from general script failures.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/06
 */
class ResponseAssertionsTest : BasePlatformTestCase() {
    fun testScriptAssertionFailureShowsItsReasonWithoutInventingAnAssertionCount() {
        val panel = ResponseTabPanel(project)
        val reason = "Expected status 201 but got 200"

        panel.showResponse(200, emptyMap(), "{}", assertionsPassed = false, executionFailure = reason)

        assertEquals(reason, assertionsText(panel))
    }

    fun testRuntimeFailureWithoutAssertionRowsIsNotLabeledAsAnAssertionFailure() {
        val panel = ResponseTabPanel(project)
        val reason = "Script execution was cancelled."

        panel.showResponse(200, emptyMap(), "{}", assertionsPassed = false, executionFailure = reason)

        assertEquals(reason, assertionsText(panel))
    }

    fun testSuccessWithoutAssertionsClearsThePreviousFailure() {
        val panel = ResponseTabPanel(project)
        panel.showResponse(200, emptyMap(), "{}", assertionsPassed = false, executionFailure = "Previous failure")

        panel.showResponse(200, emptyMap(), "{}")

        assertEquals("No assertions were reported.", assertionsText(panel))
    }

    fun testReportedAssertionsRetainTheirActualCountAndDetails() {
        val panel = ResponseTabPanel(project)
        panel.showResponse(
            200, emptyMap(), "{}", assertionsPassed = false, assertionResults = listOf(
                ResponseViewer.AssertionRow("status", true, "matched"),
                ResponseViewer.AssertionRow("$.name", false, "Expected tester but got other")
            )
        )

        val text = assertionsText(panel)
        assertTrue(text, text.startsWith("FAILED (2 assertions)"))
        assertTrue(text, text.contains("status: matched"))
        assertTrue(text, text.contains("$.name: Expected tester but got other"))
    }

    private fun assertionsText(panel: ResponseTabPanel): String {
        val tabs = descendants(panel).filterIsInstance<JTabbedPane>().single()
        val assertions = tabs.getComponentAt(tabs.indexOfTab("Assertions"))
        return descendants(assertions).filterIsInstance<JTextArea>().single().text
    }

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
    }
}
