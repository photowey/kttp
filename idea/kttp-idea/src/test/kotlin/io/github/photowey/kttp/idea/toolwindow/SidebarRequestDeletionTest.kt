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

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightPlatformTestCase
import io.github.photowey.kttp.idea.script.toKttpScript
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Verifies sidebar deletion removes only syntactically safe request statements.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class SidebarRequestDeletionTest : LightPlatformTestCase() {
    fun testDeletingAStandaloneRequestLeavesTheOtherRequestValid() {
        assertSafeDeletion("GET(\"http://localhost/first\")\nPOST(\"http://localhost/keep\")")
    }

    fun testDeletingARequestWithThenRemovesTheWholeReceiverChain() {
        assertSafeDeletion(
            """
            GET("http://localhost/first").then { println("first") }.then { println("second") }
            POST("http://localhost/keep")
        """.trimIndent()
        )
    }

    fun testPropertyInitializersCannotBeDeletedAsPartialExpressions() {
        assertDeletionDisabled("val response = GET(\"http://localhost/first\")")
        assertDeletionDisabled("val response = GET(\"http://localhost/first\").then { println(\"ok\") }")
    }

    fun testWrappedRequestsRemainForTheScriptEditor() {
        assertDeletionDisabled("consume(GET(\"http://localhost/first\"))")
        assertDeletionDisabled("if (enabled) GET(\"http://localhost/first\") else fallback()")
        assertDeletionDisabled("(GET(\"http://localhost/first\")).then { println(\"ok\") }")
    }

    private fun assertSafeDeletion(source: String) {
        val file = parse(source)
        val first = requireNotNull(file.toKttpScript()).findRequestDeclarations().first()
        val statement = requireNotNull(deletableRequestStatement(requireNotNull(first.element)))
        val updated = source.removeRange(statement.textRange.startOffset, statement.textRange.endOffset)
        val reparsed = parse(updated)
        assertNull(PsiTreeUtil.findChildOfType(reparsed, PsiErrorElement::class.java))
        val remaining = requireNotNull(reparsed.toKttpScript()).findRequestDeclarations()
        assertEquals(1, remaining.size)
        assertEquals("POST", remaining.single().getHttpMethod())
        assertFalse(updated.contains(".then"))
    }

    private fun assertDeletionDisabled(source: String) {
        val file = parse(source)
        assertNull(PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java))
        val declaration = requireNotNull(file.toKttpScript()).findRequestDeclarations().single()
        assertNull(deletableRequestStatement(requireNotNull(declaration.element)))
    }

    private fun parse(source: String): KtFile = KtPsiFactory(project, false).createFile("delete.kttp.kts", source)
}
