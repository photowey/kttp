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

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression

/**
 * Request semantics outside its own lambda, including script-wide client configuration.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal object ProjectionOuterSemantics {
    private val postProcessingCalls = setOf("then", "thenResponse", "decode", "jsonPath")

    fun extract(element: KtCallExpression, file: KtFile?): List<SemanticGap> {
        val gaps = mutableListOf<SemanticGap>()
        extractChainedGaps(element, gaps)
        val scriptFile = file ?: element.containingFile as? KtFile
        if (scriptFile != null && hasClientConfiguration(scriptFile)) {
            gaps += unsupportedGap(
                "configureClient",
                "Script client configuration requires script execution to preserve request semantics."
            )
        }
        return gaps
    }

    fun postProcessingGap(callee: String): SemanticGap? {
        if (callee !in postProcessingCalls) return null
        return SemanticGap(
            GapKind.POST_PROCESSING_ONLY, callee,
            "Post-processing call '$callee' will be skipped by GUI Send."
        )
    }

    fun unsupportedGap(location: String, message: String): SemanticGap =
        SemanticGap(GapKind.REQUEST_SHAPING_UNSUPPORTED, location, message)

    private fun extractChainedGaps(element: KtCallExpression, gaps: MutableList<SemanticGap>) {
        var current: PsiElement = element
        while (true) {
            val parent = current.parent ?: break
            val chained = parent is KtQualifiedExpression && parent.receiverExpression == current
            val outerCall = when {
                chained -> (parent as KtQualifiedExpression).selectorExpression as? KtCallExpression
                parent is KtCallExpression -> parent
                else -> null
            }
            if (outerCall != null) {
                val callee = (outerCall.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
                val postProcessing = callee?.let(::postProcessingGap)
                if (postProcessing != null) {
                    gaps += postProcessing
                } else if (chained) {
                    gaps += unsupportedGap(
                        callee ?: "request",
                        "Chained call '${callee ?: outerCall.text}' is not supported by the GUI composer."
                    )
                }
            }
            current = parent
        }
    }

    private fun hasClientConfiguration(file: KtFile): Boolean {
        val names = mutableSetOf("configureClient")
        file.importDirectives.forEach { directive ->
            if (directive.importedFqName?.shortName()?.asString() == "configureClient") {
                directive.aliasName?.let(names::add)
            }
        }
        // Inspect references, not source text: comments and static strings have no effect.
        // References inside wrappers and callable aliases are conservatively blocking even
        // when static PSI cannot prove whether or when their configuration is invoked.
        return PsiTreeUtil.findChildrenOfType(file, KtNameReferenceExpression::class.java).any { reference ->
            reference.getReferencedName() in names &&
                PsiTreeUtil.getParentOfType(reference, KtImportDirective::class.java) == null
        }
    }
}
