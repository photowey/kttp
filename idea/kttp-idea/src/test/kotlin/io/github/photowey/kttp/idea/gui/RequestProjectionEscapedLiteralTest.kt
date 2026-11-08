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

import com.intellij.testFramework.LightPlatformTestCase
import io.github.photowey.kttp.idea.script.toKttpScript
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Verifies that static string values retain escapes while dynamic interpolation requires script execution.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class RequestProjectionEscapedLiteralTest : LightPlatformTestCase() {

    fun testQuotedJsonBodyIsProjectedWithoutLosingEscapes() {
        val projection = extract(
            """
            POST("https://example.com") {
                contentType("application/json")
                body("{\"name\":\"Alice\",\"note\":\"line1\\nline2\"}")
            }
            """.trimIndent()
        )

        assertEquals(GuiMode.GUI_READY, projection.mode)
        assertEquals(
            BodySpec.Json(ValueRef.Literal("""{"name":"Alice","note":"line1\nline2"}""")),
            projection.spec?.body
        )
    }

    fun testEscapedLiteralAliasIsProjectedAsItsRuntimeValue() {
        val projection = extract(
            """
            val payload = "first\nsecond\t\u4e2d\\end"
            val alias = payload
            POST("https://example.com") { body(alias) }
            """.trimIndent()
        )

        assertEquals(GuiMode.GUI_READY, projection.mode)
        assertEquals(BodySpec.Raw(ValueRef.Literal("first\nsecond\t中\\end")), projection.spec?.body)
    }

    fun testLiteralDollarIsNotTreatedAsKotlinInterpolation() {
        val projection = extract(
            """
            GET("https://example.com/\${'$'}amount") {
                query("price", "\${'$'}42")
                header("X-Quoted", "say \"hi\"")
            }
            """.trimIndent()
        )

        assertEquals(GuiMode.GUI_READY, projection.mode)
        assertEquals(ValueRef.Literal("https://example.com/${'$'}amount"), projection.spec?.target?.url)
        assertEquals(listOf(NameValueRef("price", ValueRef.Literal("${'$'}42"))), projection.spec?.target?.queryParams)
        assertEquals(listOf(HeaderSpec("X-Quoted", ValueRef.Literal("say \"hi\""))), projection.spec?.headers)
    }

    fun testEscapedParameterNamesAreDecodedToo() {
        val projection = extract(
            """
            POST("https://example.com") {
                query("quoted\"key", "value")
                header("X-\u0041", "value")
                formData { field("quoted\"field", "value") }
            }
            """.trimIndent()
        )

        assertEquals(GuiMode.GUI_READY, projection.mode)
        assertEquals("quoted\"key", projection.spec?.target?.queryParams?.single()?.name)
        assertEquals("X-A", projection.spec?.headers?.single()?.name)
        assertEquals("quoted\"field", (projection.spec?.body as BodySpec.FormUrlEncoded).fields.single().name)
    }

    fun testActualInterpolationRemainsScriptOnly() {
        for (value in listOf("\"${'$'}name\"", "\"${'$'}{name.uppercase()}\"")) {
            val projection = extract(
                "val name = \"Alice\"\nPOST(\"https://example.com\") { body($value) }"
            )
            assertEquals(GuiMode.SCRIPT_ONLY, projection.mode)
            assertNull(projection.spec)
        }
    }

    fun testRawStringKeepsBackslashSequencesLiteral() {
        val projection = extract(
            "POST(\"https://example.com\") { body(\"\"\"raw\\ntext\"\"\") }"
        )

        assertEquals(GuiMode.GUI_READY, projection.mode)
        assertEquals(BodySpec.Raw(ValueRef.Literal("raw\\ntext")), projection.spec?.body)
    }

    fun testJsonLookingRawBodyDoesNotInventContentType() {
        val projection = extract("""POST("https://example.com") { body("{}") }""")

        assertEquals(GuiMode.GUI_READY, projection.mode)
        assertEquals(BodySpec.Raw(ValueRef.Literal("{}")), projection.spec?.body)
        assertTrue(projection.spec?.headers.isNullOrEmpty())
    }

    fun testExplicitTextTypeKeepsJsonLookingBodyRaw() {
        val projection = extract(
            """
            POST("https://example.com") {
                body("{}")
                header("content-type", "text/plain")
            }
            """.trimIndent()
        )

        assertEquals(GuiMode.GUI_READY, projection.mode)
        assertEquals(BodySpec.Raw(ValueRef.Literal("{}")), projection.spec?.body)
        assertEquals(listOf(HeaderSpec("content-type", ValueRef.Literal("text/plain"))), projection.spec?.headers)
    }

    fun testExplicitJsonTypeIsRecognizedAfterBodyAndPreservedExactly() {
        val projection = extract(
            """
            POST("https://example.com") {
                body("{}")
                contentType("application/problem+json; charset=UTF-8")
            }
            """.trimIndent()
        )

        assertEquals(GuiMode.GUI_READY, projection.mode)
        assertEquals(BodySpec.Json(ValueRef.Literal("{}")), projection.spec?.body)
        assertEquals(
            listOf(HeaderSpec("Content-Type", ValueRef.Literal("application/problem+json; charset=UTF-8"))),
            projection.spec?.headers
        )
    }

    fun testEnvironmentContentTypeDoesNotGuessJson() {
        val projection = extract(
            """
            val contentTypeValue: String by env
            POST("https://example.com") {
                contentType(contentTypeValue)
                body("{}")
            }
            """.trimIndent()
        )

        assertEquals(GuiMode.GUI_READY, projection.mode)
        assertEquals(BodySpec.Raw(ValueRef.Literal("{}")), projection.spec?.body)
        assertEquals(ValueRef.Env("contentTypeValue"), projection.spec?.headers?.single()?.value)
    }

    fun testHeadersInfixPairsKeepDecodedLiteralValues() {
        val projection = extract(
            """
            GET("https://example.com") {
                headers("Accept" to "application/json", "X-Quoted" to "say \"hi\"")
            }
            """.trimIndent()
        )

        assertEquals(GuiMode.GUI_READY, projection.mode)
        assertEquals(
            listOf(
                HeaderSpec("Accept", ValueRef.Literal("application/json")),
                HeaderSpec("X-Quoted", ValueRef.Literal("say \"hi\""))
            ),
            projection.spec?.headers
        )
    }

    fun testLocalFileDelegatePreservesCharsetAndRequiredFlag() {
        val projection = extract(
            """
            val payload: String by localFile("payload\u0020data.json", charset = java.nio.charset.Charset.forName("GBK"), required = false)
            POST("https://example.com") { body(payload) }
            """.trimIndent()
        )

        assertEquals(GuiMode.GUI_READY, projection.mode)
        assertEquals(
            BodySpec.Raw(ValueRef.LocalFile("payload data.json", charset = "GBK", required = false)),
            projection.spec?.body
        )
    }

    fun testDynamicLocalFileOptionsRemainScriptOnly() {
        for (options in listOf("charset = findCharset()", "required = findRequired()")) {
            val projection = extract(
                "val payload: String by localFile(\"payload.json\", $options)\nPOST(\"https://example.com\") { body(payload) }"
            )
            assertEquals(GuiMode.SCRIPT_ONLY, projection.mode)
            assertNull(projection.spec)
        }
    }

    fun testInterpolatedHeaderNameRemainsScriptOnly() {
        val projection = extract(
            """
            val name = "Accept"
            GET("https://example.com") { header("${'$'}name", "application/json") }
            """.trimIndent()
        )

        assertEquals(GuiMode.SCRIPT_ONLY, projection.mode)
        assertNull(projection.spec)
    }

    private fun extract(source: String): RequestProjection {
        val file = KtPsiFactory(project, false).createFile("sample.kttp.kts", source)
        val declaration = requireNotNull(file.toKttpScript()).findRequestDeclarations().single()
        return RequestProjectionExtractor.extract(declaration, file, 0, "/tmp/sample.kttp.kts")
    }
}
