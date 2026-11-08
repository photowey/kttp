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

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import io.github.photowey.kttp.idea.gui.GuiBridgeFormatter
import io.github.photowey.kttp.idea.gui.GuiMode
import io.github.photowey.kttp.idea.gui.RequestProjectionExtractor
import io.github.photowey.kttp.idea.script.KttpRequestDeclaration
import io.github.photowey.kttp.idea.script.toKttpScript
import org.jetbrains.kotlin.psi.KtFile

/**
 * Display values and smart declaration pointers; no raw PSI file is retained.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal data class SidebarRequestDisplayInfo(
    val declaration: KttpRequestDeclaration,
    val method: String,
    val url: String,
    val mode: GuiMode,
    val tooltip: String
)

/**
 * Reads script requests and their projection capabilities for sidebar display.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal class SidebarRequestParser(private val project: Project) {
    fun read(file: VirtualFile): List<SidebarRequestDisplayInfo> =
        ReadAction.compute<List<SidebarRequestDisplayInfo>, RuntimeException> {
            val psiFile = PsiManager.getInstance(project).findFile(file) as? KtFile ?: return@compute emptyList()
            collectRequestDisplayInfo(psiFile, file.path)
        }

    private fun collectRequestDisplayInfo(psiFile: KtFile, sourceFilePath: String): List<SidebarRequestDisplayInfo> {
        val script = psiFile.toKttpScript() ?: return emptyList()
        return script.findRequestDeclarations().mapIndexed { index, decl ->
            val projection = RequestProjectionExtractor.extract(
                declaration = decl,
                ktFile = psiFile,
                declarationIndex = index,
                sourceFilePath = sourceFilePath
            )
            val explanation = RequestProjectionPresentation.badge(projection.mode).tooltip
            SidebarRequestDisplayInfo(
                declaration = decl,
                method = decl.getHttpMethod() ?: "GET",
                url = decl.getUrl() ?: "",
                mode = projection.mode,
                tooltip = if (projection.gaps.isEmpty()) explanation else
                    "$explanation\n\n${GuiBridgeFormatter.formatGaps(projection.gaps, "")}"
            )
        }
    }

}
