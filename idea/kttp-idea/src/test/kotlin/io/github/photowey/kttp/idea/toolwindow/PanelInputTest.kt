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

import io.github.photowey.kttp.idea.gui.AuthSpec
import io.github.photowey.kttp.idea.gui.ApiKeyPlacement
import io.github.photowey.kttp.idea.gui.ValueRef
import org.junit.Test
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies request panel reset behavior, editable rows, and read-only control restrictions.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class PanelInputTest {
    @Test
    fun loadingAnEmptyBodyClearsPayloadsFromThePreviousRequest() = onEdt {
        val panel = BodyTabPanel()
        panel.setBody("json", "{\"previous\":true}")
        panel.setBody("raw", "previous raw body")
        panel.setBody("form", "note=previous")
        panel.setBody("none", "")

        val selector = descendants(panel).filterIsInstance<JComboBox<*>>().single()
        selector.selectedItem =
            (0 until selector.itemCount).map { selector.getItemAt(it) }.single { it.toString() == "JSON" }
        assertEquals("json" to "", panel.getBody())
        selector.selectedItem =
            (0 until selector.itemCount).map { selector.getItemAt(it) }.single { it.toString() == "Raw" }
        assertEquals("raw" to "", panel.getBody())
        selector.selectedItem = (0 until selector.itemCount).map { selector.getItemAt(it) }
            .single { it.toString() == "x-www-form-urlencoded" }
        assertTrue(panel.getFormDataFields().isEmpty())
    }

    @Test
    fun loadingInheritAuthClearsCredentialsFromThePreviousRequest() = onEdt {
        val panel = AuthTabPanel()
        panel.setAuth("bearer", mapOf("token" to "previous-token"))
        panel.setAuth("basic", mapOf("username" to "previous-user", "password" to "previous-password"))
        panel.setAuth("apikey", mapOf("keyName" to "X-Previous", "keyValue" to "previous-key"))
        panel.setAuth("inherit", emptyMap())

        val selector =
            descendants(panel).filterIsInstance<JComboBox<*>>().single { it.getItemAt(0).toString() == "Inherit" }
        val emptyVariants = mapOf(
            "Bearer Token" to AuthSpec.Bearer(ValueRef.Literal("")),
            "Basic Auth" to AuthSpec.Basic(ValueRef.Literal(""), ValueRef.Literal("")),
            "API Key" to AuthSpec.ApiKey("", ValueRef.Literal(""))
        )
        for ((type, emptyVariant) in emptyVariants) {
            selector.selectedItem =
                (0 until selector.itemCount).map { selector.getItemAt(it) }.single { it.toString() == type }
            assertEquals(emptyVariant, panel.getAuthSpec(), "Old credentials must not appear in $type")
            assertTrue(panel.getAuthHeaders().isEmpty(), "Cleared credentials must not create legacy headers")
        }
    }

    @Test
    fun readOnlyJsonCannotBeChangedByTheBeautifyButton() = onEdt {
        val panel = BodyTabPanel()
        val source = "{\"source\":true}"
        panel.setBody("json", source)
        panel.setEditable(false)

        val beautify = button(panel, "Beautify JSON")
        assertFalse(beautify.isEnabled)
        beautify.doClick()
        assertEquals("json" to source, panel.getBody())
    }

    @Test
    fun aNewFormHasAnEditableRowAndAnAddFieldButton() = onEdt {
        val panel = BodyTabPanel()
        val selector = descendants(panel).filterIsInstance<JComboBox<*>>().single()
        selector.selectedItem = (0 until selector.itemCount).map { selector.getItemAt(it) }
            .single { it.toString() == "x-www-form-urlencoded" }
        val table = descendants(panel).filterIsInstance<JTable>().single()
        assertTrue(table.rowCount > 0, "A new form must allow entering its first field")
        val before = table.rowCount
        button(panel, "Add Field").doClick()
        assertEquals(before + 1, table.rowCount)
        panel.setEditable(false)
        assertFalse(button(panel, "Add Field").isEnabled)
    }

    @Test
    fun readOnlyTablesCannotAddParametersOrHeaders() = onEdt {
        val params = ParamsTabPanel()
        val headers = HeadersTabPanel()
        params.setEditable(false)
        headers.setEditable(false)
        assertFalse(button(params, "Add Parameter").isEnabled)
        assertFalse(button(headers, "Add Header").isEnabled)
    }

    @Test
    fun pendingParameterCellIsCommittedBeforeAnActionReadsTheRequest() = onEdt {
        val panel = ParamsTabPanel()
        panel.setParamEntries(listOf("q" to "old", "q" to "second"))
        val table = descendants(panel).filterIsInstance<JTable>().single()
        // The editor's normal listener reads the request for the Bridge preview.
        // Reading must not try to stop the same editor again during this callback.
        panel.getTableModel().addTableModelListener { panel.getParamEntries() }
        assertTrue(table.editCellAt(0, 1))
        (table.editorComponent as JTextField).text = " last value "

        var submitted: List<Pair<String, String>>? = null
        JButton("Send").apply {
            addActionListener {
                if (panel.commitPendingEdits()) submitted = panel.getParamEntries()
            }
        }.doClick()

        assertEquals(listOf("q" to " last value ", "q" to "second"), submitted)
        assertFalse(table.isEditing)
    }

    @Test
    fun pendingHeaderCellIsCommittedWithoutCollapsingRepeatedHeaderNames() = onEdt {
        val panel = HeadersTabPanel()
        panel.setHeaderEntries(listOf("X-Tag" to "old", "X-Tag" to "second"))
        val table = descendants(panel).filterIsInstance<JTable>().single()
        panel.getTableModel().addTableModelListener { panel.getHeaderEntries() }
        assertTrue(table.editCellAt(0, 1))
        (table.editorComponent as JTextField).text = "latest"

        assertTrue(panel.commitPendingEdits())
        assertEquals(listOf("X-Tag" to "latest", "X-Tag" to "second"), panel.getHeaderEntries())
        assertFalse(table.isEditing)
    }

    @Test
    fun structuredFormAndEncodedRoundTripPreserveRepeatedKeysAndExactValues() = onEdt {
        val fields = listOf("note" to " a&b=c+d% ", "note" to "雪", "empty" to "")
        val panel = BodyTabPanel()
        panel.setFormDataFields(fields)
        assertEquals(fields, panel.getFormDataFields())

        val (type, encoded) = panel.getBody()
        val restored = BodyTabPanel()
        restored.setBody(type, encoded)
        assertEquals(fields, restored.getFormDataFields())
    }

    @Test
    fun pendingFormCellIsCommittedBeforeReadingItsFields() = onEdt {
        val panel = BodyTabPanel()
        panel.setFormDataFields(listOf("note" to "old"))
        panel.addChangeListener { panel.getFormDataFields() }
        val table = descendants(panel).filterIsInstance<JTable>().single()
        assertTrue(table.editCellAt(0, 1))
        (table.editorComponent as JTextField).text = " a&b "

        assertTrue(panel.commitPendingEdits())
        assertEquals(listOf("note" to " a&b "), panel.getFormDataFields())
        assertFalse(table.isEditing)
    }

    @Test
    fun loadingAQueryApiKeyPreservesItsPlacementAndAllowsChangingIt() = onEdt {
        val panel = AuthTabPanel()
        panel.setAuth("apiKey", mapOf("keyName" to "api_key", "keyValue" to "secret"), ApiKeyPlacement.QUERY)
        assertEquals(AuthSpec.ApiKey("api_key", ValueRef.Literal("secret"), ApiKeyPlacement.QUERY), panel.getAuthSpec())
        assertTrue(panel.getAuthHeaders().isEmpty(), "Query API keys must not also become request headers")

        val placement = descendants(panel).filterIsInstance<JComboBox<*>>().single {
            it.getItemAt(0) == ApiKeyPlacement.HEADER
        }
        placement.selectedItem = ApiKeyPlacement.HEADER
        assertEquals(
            AuthSpec.ApiKey("api_key", ValueRef.Literal("secret"), ApiKeyPlacement.HEADER),
            panel.getAuthSpec()
        )
        assertEquals(mapOf("api_key" to "secret"), panel.getAuthHeaders())
        panel.setEditable(false)
        assertFalse(placement.isEnabled)

        panel.setAuth("inherit", emptyMap())
        assertEquals(ApiKeyPlacement.HEADER, placement.selectedItem)
    }

    @Test
    fun authInputsUseAvailableWidthWithoutBecomingMultilineBoxes() = onEdt {
        val panel = AuthTabPanel()
        panel.setSize(900, 420)
        val selector =
            descendants(panel).filterIsInstance<JComboBox<*>>().single { it.getItemAt(0).toString() == "Inherit" }
        val expectedInputs = mapOf(
            "Bearer Token" to listOf("Enter Bearer Token"),
            "Basic Auth" to listOf("Enter username", "Enter password"),
            "API Key" to listOf("API Key name (e.g., X-API-Key)", "API Key value")
        )
        for ((type, tooltips) in expectedInputs) {
            selector.selectedItem =
                (0 until selector.itemCount).map { selector.getItemAt(it) }.single { it.toString() == type }
            layout(panel)
            val inputs =
                descendants(panel).filterIsInstance<JTextField>().filter { it.toolTipText in tooltips }.toList()
            assertEquals(tooltips.size, inputs.size)
            inputs.forEach { input ->
                assertTrue(input.width > 250, "$type input should expand across the available form width")
                assertTrue(input.height <= input.preferredSize.height + 4, "$type input must keep a single-line height")
            }
        }
    }

    private fun onEdt(action: () -> Unit) = SwingUtilities.invokeAndWait(action)

    private fun button(panel: Component, text: String): JButton =
        assertNotNull(descendants(panel).filterIsInstance<JButton>().firstOrNull { it.text == text })

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
    }

    private fun layout(container: Container) {
        container.doLayout()
        container.components.filterIsInstance<Container>().forEach(::layout)
    }
}
