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

package io.github.photowey.kttp.idea.run

import com.intellij.execution.filters.Filter
import com.intellij.execution.impl.InlayProvider
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.annotations.NotNull
import java.nio.file.Paths

/**
 * Inlay for the platform's reveal-file action after a response file path.
 * The link text comes from [RevealFileAction]; clicking invokes that action for the response file.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/18
 */
class RevealFileInlay(
    offset: Int,
    @param:NotNull private val filePath: String
) : Filter.Result(offset, offset, null), InlayProvider {

    @NotNull
    override fun createInlayRenderer(@NotNull editor: Editor): EditorCustomElementRenderer {
        val editorImpl = editor as? EditorImpl ?: return EmptyRenderer()
        val factory = com.intellij.codeInsight.hints.presentation.PresentationFactory(editor)

        // Use the platform action name for the link text.
        val actionName = RevealFileAction.getActionName()
        val textPresentation = factory.smallText(actionName)
        val roundedPresentation = factory.roundWithBackground(textPresentation)

        // Invoke the platform's reveal-file action when the link is clicked.
        val clickListener = object : com.intellij.codeInsight.hints.InlayPresentationFactory.ClickListener {
            override fun onClick(event: java.awt.event.MouseEvent, translated: java.awt.Point) {
                val project = editorImpl.project
                if (project != null) {
                    RevealFileAction.openFile(Paths.get(filePath))
                }
            }
        }
        val presentation = factory.referenceOnHover(roundedPresentation, clickListener)

        return com.intellij.codeInsight.hints.presentation.PresentationRenderer(presentation)
    }

    /**
     * Provides an invisible, zero-width anchor for the response-file reveal action.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/18
     */
    private class EmptyRenderer : EditorCustomElementRenderer {
        override fun paint(
            inlay: com.intellij.openapi.editor.Inlay<EditorCustomElementRenderer>,
            g: java.awt.Graphics,
            r: java.awt.Rectangle,
            textAttributes: com.intellij.openapi.editor.markup.TextAttributes
        ) {
        }

        override fun calcWidthInPixels(inlay: com.intellij.openapi.editor.Inlay<EditorCustomElementRenderer>): Int = 0
    }
}
