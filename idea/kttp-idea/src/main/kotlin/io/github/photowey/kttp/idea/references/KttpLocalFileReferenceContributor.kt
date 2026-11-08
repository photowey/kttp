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

package io.github.photowey.kttp.idea.references

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.util.ProcessingContext
import io.github.photowey.kttp.idea.lang.KttpFileType
import io.github.photowey.kttp.idea.substitutor.KttpLanguageSubstitutor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import java.io.File
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.patterns.PlatformPatterns

/**
 * Enables Ctrl+Click (Navigate to Declaration) on the path string inside localFile("path")
 * so that the referenced file opens in the editor.
 * Path resolution: project root (if available) or script file directory, same as run --base-dir.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/18
 */
class KttpLocalFileReferenceContributor : com.intellij.psi.PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: com.intellij.psi.PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    val stringExpr = element as? KtStringTemplateExpression
                        ?: element.getParentOfType<KtStringTemplateExpression>(false)
                        ?: return PsiReference.EMPTY_ARRAY
                    val file = element.containingFile ?: return PsiReference.EMPTY_ARRAY
                    if (!file.name.endsWith("." + KttpFileType.INSTANCE.defaultExtension)) return PsiReference.EMPTY_ARRAY
                    val valueArg = stringExpr.getParentOfType<KtValueArgument>(false) ?: return PsiReference.EMPTY_ARRAY
                    val call = valueArg.getParentOfType<KtCallExpression>(false) ?: return PsiReference.EMPTY_ARRAY
                    if ((call.calleeExpression as? org.jetbrains.kotlin.psi.KtReferenceExpression)?.text != KttpLanguageSubstitutor.LOCAL_FILE_FUNCTION_NAME)
                        return PsiReference.EMPTY_ARRAY
                    if (call.valueArguments.firstOrNull() != valueArg) return PsiReference.EMPTY_ARRAY
                    val pathArg =
                        KttpLanguageSubstitutor.getLocalFilePathArgument(call) ?: return PsiReference.EMPTY_ARRAY
                    return arrayOf(
                        KttpLocalFileReference(
                            stringExpr,
                            pathArg,
                            file.virtualFile?.parent?.path,
                            element.project
                        )
                    )
                }
            }
        )
    }
}

/**
 * Resolves to the PsiFile of the path inside localFile("...").
 * Ctrl+Click navigates to that file.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/18
 */
private class KttpLocalFileReference(
    element: KtStringTemplateExpression,
    private val pathArg: String,
    private val scriptDir: String?,
    private val project: Project
) : com.intellij.psi.PsiReferenceBase<KtStringTemplateExpression>(element) {

    override fun resolve(): PsiElement? {
        val baseDir = project.basePath ?: scriptDir ?: return null
        val resolvedPath = if (pathArg.startsWith("/")) pathArg else File(baseDir, pathArg).absolutePath
        val ioFile = File(resolvedPath)
        if (!ioFile.exists()) return null
        val vf = LocalFileSystem.getInstance().findFileByIoFile(ioFile) ?: return null
        return PsiManager.getInstance(project).findFile(vf)
    }
}
