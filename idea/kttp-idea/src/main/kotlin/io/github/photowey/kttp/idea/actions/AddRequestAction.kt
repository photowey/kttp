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

package io.github.photowey.kttp.idea.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import io.github.photowey.kttp.idea.lang.KttpFileType
import javax.swing.Icon

/**
 * Inserts a request template (GET/POST/...) at caret in .kttp.kts files.
 * Used by Add Request group and Generate menu.
 * [caretOffsetInTemplate] is the offset inside [template] where caret should go (e.g. inside braces).
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
open class AddRequestAction(
    private val template: String,
    private val caretOffsetInTemplate: Int,
    text: String,
    description: String,
    icon: Icon? = null,
) : AnAction(text, description, icon) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        if (!file.name.endsWith("." + KttpFileType.INSTANCE.defaultExtension)) return

        // Use WriteCommandAction to properly handle document modification with undo support
        WriteCommandAction.runWriteCommandAction(project) {
            val caret = editor.caretModel.currentCaret
            val offset = caret.offset
            val document = editor.document
            val lineStart = document.getLineStartOffset(document.getLineNumber(offset))
            val column = offset - lineStart
            val indent = if (column > 0) " ".repeat(column) else ""

            // Check if we need to add a newline before the template
            val needsNewline =
                offset > 0 && !document.text.substring((offset - 1).coerceAtLeast(0), offset).contains("\n")
            val prefix = if (needsNewline) "\n" else ""

            val textToInsert = prefix + template.lines().joinToString("\n") { if (it.isBlank()) it else indent + it }
            document.insertString(offset, textToInsert)
            editor.caretModel.moveToOffset(
                offset + prefix.length + caretOffsetInTemplate.coerceIn(
                    0,
                    textToInsert.length - prefix.length
                )
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: e.getData(CommonDataKeys.PSI_FILE)?.virtualFile
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = e.project != null &&
            file != null &&
            file.name.endsWith("." + KttpFileType.INSTANCE.defaultExtension) &&
            editor != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/**
 * GET("url") { }
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
open class AddGetRequestAction : AddRequestAction(
    template = """GET("https://httpbin.org/get") {
    header("Accept", "application/json")
}""",
    caretOffsetInTemplate = "GET(\"https://httpbin.org/get\") {\n    ".length,
    text = "GET",
    description = "Insert GET request",
    icon = AllIcons.Actions.Show,
)

/**
 * POST("url") { body(...) }
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
open class AddPostRequestAction : AddRequestAction(
    template = """POST("https://httpbin.org/post") {
    header("Content-Type", "application/json")
    body(${'"'}${'"'}${'"'}
        {
            "key": "value"
        }
    ${'"'}${'"'}${'"'}.trimIndent())
}""",
    caretOffsetInTemplate = "POST(\"https://httpbin.org/post\") {\n    ".length,
    text = "POST",
    description = "Insert POST request with JSON body",
    icon = AllIcons.ToolbarDecorator.Import,
)

/**
 * PUT("url") { body(...) }
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
open class AddPutRequestAction : AddRequestAction(
    template = """PUT("https://httpbin.org/put") {
    header("Content-Type", "application/json")
    body(${'"'}${'"'}${'"'}
        {
            "key": "value"
        }
    ${'"'}${'"'}${'"'}.trimIndent())
}""",
    caretOffsetInTemplate = "PUT(\"https://httpbin.org/put\") {\n    ".length,
    text = "PUT",
    description = "Insert PUT request with JSON body",
    icon = AllIcons.ToolbarDecorator.Export,
)

/**
 * DELETE("url") { }
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
open class AddDeleteRequestAction : AddRequestAction(
    template = """DELETE("https://httpbin.org/delete") {
    header("Accept", "application/json")
}""",
    caretOffsetInTemplate = "DELETE(\"https://httpbin.org/delete\") {\n    ".length,
    text = "DELETE",
    description = "Insert DELETE request",
    icon = AllIcons.Actions.Cancel,
)

/**
 * PATCH("url") { body(...) }
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
open class AddPatchRequestAction : AddRequestAction(
    template = """PATCH("https://httpbin.org/patch") {
    header("Content-Type", "application/json")
    body(${'"'}${'"'}${'"'}
        {
            "key": "value"
        }
    ${'"'}${'"'}${'"'}.trimIndent())
}""",
    caretOffsetInTemplate = "PATCH(\"https://httpbin.org/patch\") {\n    ".length,
    text = "PATCH",
    description = "Insert PATCH request with JSON body",
    icon = AllIcons.Actions.EditSource,
)

/**
 * HEAD("url") { }
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
open class AddHeadRequestAction : AddRequestAction(
    template = """HEAD("https://httpbin.org/get") {
    header("Accept", "application/json")
}""",
    caretOffsetInTemplate = "HEAD(\"https://httpbin.org/get\") {\n    ".length,
    text = "HEAD",
    description = "Insert HEAD request",
    icon = AllIcons.Actions.ShowCode,
)

/**
 * OPTIONS("url") { }
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
open class AddOptionsRequestAction : AddRequestAction(
    template = """OPTIONS("https://httpbin.org/get") {
    header("Accept", "application/json")
}""",
    caretOffsetInTemplate = "OPTIONS(\"https://httpbin.org/get\") {\n    ".length,
    text = "OPTIONS",
    description = "Insert OPTIONS request",
    icon = AllIcons.Actions.More,
)

/**
 * request("METHOD", "url") { }
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
open class AddCustomRequestAction : AddRequestAction(
    template = """request("CUSTOM", "https://httpbin.org/anything") {
    header("Accept", "application/json")
}""",
    caretOffsetInTemplate = "request(\"CUSTOM\", \"https://httpbin.org/anything\") {\n    ".length,
    text = "Custom request",
    description = "Insert custom HTTP method request",
    icon = AllIcons.Actions.EditSource,
)
