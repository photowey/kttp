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

package io.github.photowey.kttp.idea.script

import com.intellij.psi.util.PsiTreeUtil
import io.github.photowey.kttp.core.context.execution.DeclarationCoordinates
import org.jetbrains.kotlin.psi.KtCallExpression

/** Shared by GUI Send and Run: source coordinates must identify just one DSL call site. */
internal fun KttpRequestDeclaration.sourceLines(): DeclarationCoordinates.SourceLines {
    val call = requireNotNull(element) { "The selected request was removed." }
    val document = requireNotNull(call.containingFile.viewProvider.document) { "Cannot read the source script." }
    fun lines(expression: KtCallExpression) = DeclarationCoordinates.SourceLines(
        document.getLineNumber(expression.textRange.startOffset) + 1,
        document.getLineNumber((expression.textRange.endOffset - 1).coerceAtLeast(expression.textRange.startOffset)) + 1
    )

    val selected = lines(call)
    val overlapping = PsiTreeUtil.collectElementsOfType(call.containingFile, KtCallExpression::class.java).count {
        it.toKttpRequestDeclaration() != null && lines(it).let { other ->
            other.first <= selected.last && selected.first <= other.last
        }
    }
    require(overlapping == 1) { "Ambiguous request source lines. Put each request declaration on separate lines." }
    return selected
}
