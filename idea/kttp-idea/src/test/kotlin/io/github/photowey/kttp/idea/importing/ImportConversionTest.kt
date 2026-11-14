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

import io.github.photowey.kttp.idea.gui.ValueRef
import org.junit.Test
import kotlin.test.*

/**
 * Verifies imported semantics survive script generation and conservative GUI adaptation.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
class ImportConversionTest {
    @Test
    fun simpleRequestRemainsEditableAndEscapesDollar() {
        val request = ImportedRequest(
            "Create", "POST", ImportValue("https://example.com/"),
            body = ImportBody.Text(ImportValue("\$value"), "application/json")
        )
        val draft = assertNotNull(ImportRequestAdapter.adapt(request))
        assertEquals(ValueRef.Literal("https://example.com/"), draft.request.target.url)
        val script = ImportScriptRenderer.render(request)
        assertTrue(script.contains("POST("))
        assertTrue(script.contains("application/json"))
        assertTrue(script.contains("\\\$value"), script)
    }

    @Test
    fun mixedEnvironmentUsesSafeExpressionAndFollowScript() {
        val request = ImportedRequest(
            "Read", "GET", ImportValue("{{baseUrl}}/users/{{id}}", true),
            variables = mapOf("id" to "42")
        )
        assertNull(ImportRequestAdapter.adapt(request))
        val script = ImportScriptRenderer.render(request)
        assertTrue(script.contains("by env"), script)
        assertFalse(script.contains("{{"), script)
        assertTrue(script.contains("42"), script)
    }

    @Test
    fun customAndMultipartArePreservedAsScripts() {
        val request = ImportedRequest(
            "Upload", "CUSTOM", ImportValue("https://example.com/upload"),
            body = ImportBody.Multipart(listOf(ImportPart("file", ImportValue("a.bin"), file = true)))
        )
        assertNull(ImportRequestAdapter.adapt(request))
        val script = ImportScriptRenderer.render(request, baseDirectory = "/tmp")
        assertTrue(script.contains("request(\"CUSTOM\""), script)
        assertTrue(script.contains("multipart"), script)
        assertTrue(script.contains("/tmp/a.bin"), script)
        assertTrue(script.contains("readBytes()"), script)
    }

    @Test
    fun missingVariablePreservesItsReference() {
        val request = ImportedRequest("Read", "GET", ImportValue("{{host}}", true))
        val draft = assertNotNull(ImportRequestAdapter.adapt(request))
        assertEquals(ValueRef.Env("host"), draft.request.target.url)
        assertTrue(ImportScriptRenderer.render(request).contains("val `host`: String by env"))
    }

    @Test
    fun mixedReferencesUseSharedDeclarationsAndKotlinTemplates() {
        val request = ImportedRequest(
            "Read", "GET", ImportValue("{{baseUrl}}/echo?source=postman", true),
            headers = listOf(ImportField("X-Host", ImportValue("prefix-{{baseUrl}}", true)))
        )
        val source = ImportScriptRenderer.render(request)
        assertFalse(source.contains("run {"), source)
        assertEquals(1, Regex("val `baseUrl`: String by env").findAll(source).count())
        assertTrue(source.contains("GET(\"\${baseUrl}/echo?source=postman\""), source)
        val reused = ImportScriptRenderer.render(request, "val `baseUrl`: String by env\n")
        assertFalse(reused.contains("val `baseUrl`"), reused)
        assertFailsWith<IllegalArgumentException> {
            ImportScriptRenderer.render(request, "val baseUrl = \"https://wrong.example\"\n")
        }
    }

    @Test
    fun oversizedLocalExpansionIsRejectedBeforeRendering() {
        assertFailsWith<IllegalArgumentException> {
            ImportScriptRenderer.render(
                ImportedRequest(
                    "Too large", "GET", ImportValue("https://example.test/{{large}}", true),
                    variables = mapOf("large" to "x".repeat(1_048_577))
                )
            )
        }
    }

}
