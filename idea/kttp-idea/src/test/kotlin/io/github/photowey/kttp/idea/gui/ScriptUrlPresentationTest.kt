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

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Checks URL presentation without evaluating environment delegates or arbitrary script expressions.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/14
 */
class ScriptUrlPresentationTest : BasePlatformTestCase() {
    fun testGeneratedBindingKeepsEnvironmentPlaceholderAndStaticSuffix() {
        val source = "(" + binding("baseUrl") + " + \"/echo?source=postman\")"
        assertEquals("\${baseUrl}/echo?source=postman", display(source))
    }

    fun testMultipleBindingsAndEscapedLiteralCharactersAreReadable() {
        val source = binding("base+Url") + " + \"/a+b?price=\\\$5&quote=\\\"ok\\\"\" + " + binding("token")
        assertEquals("\${base+Url}/a+b?price=\$5&quote=\"ok\"\${token}", display(source))
    }

    fun testUnsupportedExpressionsAndModifiedWrappersRemainScriptOwned() {
        val unsupported = listOf(
            "target()", "System.setProperty(\"kttp-url-test\", \"executed\")",
            "run { val baseUrl: String by env; println(baseUrl); baseUrl }",
            "run { var baseUrl: String by env; baseUrl }",
            "run { val baseUrl: String by otherDelegate; baseUrl }",
            "run { val baseUrl: String by env; otherName }",
            "run(42) { val baseUrl: String by env; baseUrl }",
            "run { arg -> val baseUrl: String by env; baseUrl }"
        )
        unsupported.forEach { assertEquals(it, "Script URL (open source to edit)", display(it)) }
        assertNull(System.getProperty("kttp-url-test"))
    }

    fun testLiteralAndWholeEnvironmentReferenceKeepExistingDisplaySyntax() {
        assertEquals(
            "https://example.test/a+b",
            ScriptUrlPresentation.display(project, ValueRef.Literal("https://example.test/a+b"))
        )
        assertEquals("\${baseUrl}", ScriptUrlPresentation.display(project, ValueRef.Env("baseUrl")))
    }

    fun testDeepAndOversizedExpressionsFallBackWithoutEvaluation() {
        assertEquals("Script URL (open source to edit)", display("(".repeat(70) + binding("baseUrl") + ")".repeat(70)))
        assertEquals("Script URL (open source to edit)", display("x".repeat(65_537)))
    }

    fun testKotlinTemplatesRemainReadableWithoutExecutingExpressions() {
        assertEquals("\${baseUrl}/echo?x=\${for}", display("\"\${baseUrl}/echo?x=\${`for`}\""))
        assertEquals("Script URL (open source to edit)", display("\"\${target()}\""))
    }

    private fun binding(name: String) = "run { val `$name`: String by env; `$name` }"
    private fun display(source: String) = ScriptUrlPresentation.display(project, ValueRef.OpaqueScript(source))
}
