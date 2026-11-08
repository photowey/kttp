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

package io.github.photowey.kttp.idea.quality

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Prevents the structural regressions identified in the 2026-11-04 review.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class KotlinStructureTest : LightPlatformTestCase() {
    fun testProductionFilesAndFunctionsStayWithinReviewedBudgets() {
        val root = repositoryRoot()
        val factory = KtPsiFactory(project, false)
        val violations = mutableListOf<String>()
        for (module in listOf("core", "idea")) {
            Files.walk(root.resolve(module)).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                    .filter { path ->
                        val parts = path.map(Path::toString)
                        "build" !in parts && parts.windowed(2).any { it == listOf("src", "main") }
                    }
                    .sorted()
                    .forEach { path ->
                        val relative = root.relativize(path)
                        val text = path.readText()
                        val lines = implementationLineCount(text)
                        if (lines > MAX_FILE_LINES) violations += "$relative: $lines implementation lines (file limit $MAX_FILE_LINES)"
                        val file = factory.createFile(path.fileName.toString(), text)
                        PsiTreeUtil.findChildrenOfType(file, KtNamedFunction::class.java).forEach { function ->
                            val size = function.text.lineSequence().count()
                            val name = function.name ?: "<anonymous>"
                            if (size > MAX_FUNCTION_LINES && name !in UI_RENDER_FUNCTIONS) {
                                violations += "$relative::$name: $size lines (function limit $MAX_FUNCTION_LINES)"
                            }
                        }
                    }
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    fun testDocumentationDoesNotIncreaseImplementationLineCount() {
        val implementation = """
            package sample

            class Sample {
                val value = 1
            }
        """.trimIndent()
        val documented =
            "/*\n * Copyright (c) 2026 The Kttp Authors.\n * Licensed under the Apache License, Version 2.0.\n */\n\n" + implementation.replace(
                "class Sample",
                "/**\n * Describes the sample.\n *\n * @author photowey\n */\nclass Sample"
            )
        assertEquals(implementationLineCount(implementation), implementationLineCount(documented))
    }

    fun testImplementationLineCountRetainsExecutableAndNonDocumentedLines() {
        val source = """
            /** Documentation. */ class Sample {
                // Implementation note.
                val text = "/* not a comment */"

                /* Implementation block note. */
            }
        """.trimIndent()
        assertEquals(6, implementationLineCount(source))
    }

    fun testLeadingImplementationCommentIsNotTreatedAsALicense() {
        assertEquals(2, implementationLineCount("/* Implementation note. */\nclass Sample"))
    }

    /** Counts physical implementation lines while excluding standalone license and KDoc blocks. */
    private fun implementationLineCount(text: String): Int {
        val lexer = KotlinLexer().apply { start(text) }
        var cursor = 0
        val result = StringBuilder(text.length)
        while (lexer.tokenType != null) {
            val start = lexer.tokenStart
            val end = lexer.tokenEnd
            val token = text.substring(start, end)
            val isHeader = lexer.tokenType == KtTokens.BLOCK_COMMENT && text.substring(0, start).isBlank() &&
                token.contains("Copyright") && token.contains("Licensed under the Apache License")
            if (lexer.tokenType == KtTokens.DOC_COMMENT || isHeader) {
                val lineStart = text.lastIndexOf('\n', start - 1) + 1
                val lineEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it }
                if (text.substring(lineStart, start).isBlank() && text.substring(end, lineEnd).isBlank()) {
                    result.append(text, cursor, lineStart)
                    cursor = if (lineEnd < text.length) lineEnd + 1 else lineEnd
                    if (isHeader && text.getOrNull(cursor) == '\n') cursor++
                } else {
                    result.append(text, cursor, start)
                    result.append(token.filter { it == '\n' })
                    cursor = end
                }
            }
            lexer.advance()
        }
        result.append(text, cursor, text.length)
        val body = result.toString()
        return body.lineSequence().count() - if (body.endsWith('\n')) 1 else 0
    }

    private fun repositoryRoot(): Path = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) {
        it.parent
    }.first { Files.isRegularFile(it.resolve("settings.gradle.kts")) && Files.isDirectory(it.resolve("core")) }

    /**
     * Defines source-size limits and the UI rendering functions exempted from function-length checks.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    companion object {
        private const val MAX_FILE_LINES = 500
        private const val MAX_FUNCTION_LINES = 80

        // Platform renderer signatures/layout may be long. This exception does not cover business methods.
        private val UI_RENDER_FUNCTIONS = setOf("getTreeCellRendererComponent", "getListCellRendererComponent")
    }
}
