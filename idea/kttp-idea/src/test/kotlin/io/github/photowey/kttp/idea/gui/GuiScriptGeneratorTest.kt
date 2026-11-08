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

import io.github.photowey.kttp.core.enums.HttpMethod
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Verifies deterministic script generation, literal escaping, and nonliteral value bindings.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class GuiScriptGeneratorTest {

    @Test
    fun renderGeneratesDeterministicScriptForGuiOwnedDraft() {
        val draft = GuiDraft(
            id = "draft-1",
            title = "List Users",
            ownership = DraftOwnership.GUI_OWNED,
            request = RequestSpec(
                identity = RequestIdentity(name = "listUsers"),
                method = HttpMethod.GET,
                target = RequestTarget(
                    url = ValueRef.Literal("https://example.com/users"),
                    queryParams = listOf(NameValueRef("page", ValueRef.Literal("1")))
                ),
                headers = listOf(HeaderSpec("Accept", ValueRef.Literal("application/json"))),
                transport = TransportSpec(noRedirect = true)
            )
        )

        val rendered = GuiScriptGenerator.render(draft)

        assertTrue(rendered.contains("""GET("https://example.com/users", name = "listUsers")"""))
        assertTrue(rendered.contains("""query("page", "1")"""))
        assertTrue(rendered.contains("""header("Accept", "application/json")"""))
        assertTrue(rendered.contains("noRedirect()"))
    }

    @Test
    fun defaultFileNameUsesTitleSlug() {
        val draft = GuiDraft(
            id = "draft-2",
            title = "Create User / Admin",
            ownership = DraftOwnership.DETACHED,
            request = RequestSpec(method = HttpMethod.POST)
        )

        assertEquals("create-user-admin.kttp.kts", GuiScriptGenerator.defaultFileName(draft))
    }

    @Test
    fun renderPreservesLiteralValuesInKotlinScriptSyntax() {
        val draft = GuiDraft(
            id = "draft-3",
            title = "Create user",
            ownership = DraftOwnership.GUI_OWNED,
            request = RequestSpec(
                identity = RequestIdentity(name = "create\"User"),
                method = HttpMethod.POST,
                target = RequestTarget(
                    url = ValueRef.Literal("https://example.com/\$host")
                ),
                headers = listOf(HeaderSpec("X-Note", ValueRef.Literal("first line\nsecond line"))),
                body = BodySpec.Json(ValueRef.Literal("{\"name\":\"\$kttp\"}")),
                transport = TransportSpec(noRedirect = true, noCookies = true, http2PriorKnowledge = true)
            )
        )

        assertEquals(
            """POST("https://example.com/\${'$'}host", name = "create\"User") {
    header("X-Note", "first line\nsecond line")
    contentType("application/json")
    body("{\"name\":\"\${'$'}kttp\"}")
    noRedirect()
    noCookies()
    http2()
}
""",
            GuiScriptGenerator.render(draft)
        )
    }

    @Test
    fun localFileOptionsAndStringBindingArePreserved() {
        val script = GuiScriptGenerator.render(
            draftWith(
                BodySpec.Raw(ValueRef.LocalFile("a\"b.json", charset = "UTF-16LE", required = false))
            )
        )

        assertTrue(script.contains("""val kttpFile0: String by localFile("a\"b.json", charset = java.nio.charset.Charset.forName("UTF-16LE"), required = false)"""))
        assertTrue(script.contains("body(kttpFile0)"))
    }

    @Test
    fun explicitJsonContentTypeIsNotOverwritten() {
        val draft = draftWith(BodySpec.Json(ValueRef.Literal("{}"))).let {
            it.copy(
                request = it.request.copy(
                    headers = listOf(
                        HeaderSpec(
                            "content-type",
                            ValueRef.Literal("application/vnd.api+json; charset=UTF-8")
                        )
                    )
                )
            )
        }

        val script = GuiScriptGenerator.render(draft)

        assertTrue(script.contains("application/vnd.api+json; charset=UTF-8"))
        assertTrue(!script.contains("contentType(\"application/json\")"))
    }

    @Test
    fun stringAndCommentTextDoNotPretendToDeclareEnvironmentProperties() {
        val existing = "val text = \"\"\"\nval baseUrl: String by env\n\"\"\"\n// val baseUrl: String by env\n"
        val draft =
            draftWith().let { it.copy(request = it.request.copy(target = RequestTarget(ValueRef.Env("baseUrl")))) }

        val script = GuiScriptGenerator.render(draft, existing)

        assertTrue(script.startsWith("val `baseUrl`: String by env\n"))
    }

    @Test
    fun environmentPropertyConflictDoesNotSilentlyReadUserLiteral() {
        val draft =
            draftWith().let { it.copy(request = it.request.copy(target = RequestTarget(ValueRef.Env("baseUrl")))) }

        assertFailsWith<IllegalArgumentException> {
            GuiScriptGenerator.render(draft, "val baseUrl = \"https://wrong.example.com\"\n")
        }
    }

    @Test
    fun existingStringEnvironmentDelegateIsReusedWhenAppending() {
        val draft =
            draftWith().let { it.copy(request = it.request.copy(target = RequestTarget(ValueRef.Env("base-url")))) }
        val existing = "val `base-url`: String by env\n"

        assertEquals("POST(`base-url`) {\n}\n", GuiScriptGenerator.render(draft, existing))
    }

    @Test
    fun impossibleJvmEnvironmentPropertyNamesFailBeforeWritingInvalidScript() {
        val draft =
            draftWith().let { it.copy(request = it.request.copy(target = RequestTarget(ValueRef.Env("api.base")))) }

        assertFailsWith<IllegalArgumentException> { GuiScriptGenerator.render(draft) }
    }

    private fun draftWith(body: BodySpec = BodySpec.None) = GuiDraft(
        id = "generator-contract",
        title = "Generator contract",
        ownership = DraftOwnership.GUI_OWNED,
        request = RequestSpec(
            method = HttpMethod.POST,
            target = RequestTarget(ValueRef.Literal("https://example.com")),
            body = body
        )
    )
}
