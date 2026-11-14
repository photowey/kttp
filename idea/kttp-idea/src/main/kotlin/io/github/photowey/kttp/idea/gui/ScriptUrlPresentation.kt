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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

/**
 * Displays static URL fragments and generated environment bindings without evaluating Kotlin.
 * Unsupported expressions remain script-owned and are identified by a readable placeholder.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/14
 */
internal object ScriptUrlPresentation {
    private const val SCRIPT_URL = "Script URL (open source to edit)"

    fun display(project: Project, value: ValueRef): String {
        if (value !is ValueRef.OpaqueScript) return GuiValueRefCodec.toDisplayValue(value)
        if (value.raw.length > 65_536) return SCRIPT_URL
        return ApplicationManager.getApplication().runReadAction(Computable {
            val expression = KtPsiFactory(project, false).createExpressionIfPossible(value.raw)
            fragment(expression, 0)?.takeIf(String::isNotBlank) ?: SCRIPT_URL
        })
    }

    private fun fragment(expression: KtExpression?, depth: Int): String? {
        if (depth > 64) return null
        return when (expression) {
            is KtParenthesizedExpression -> fragment(expression.expression, depth + 1)
            is KtBinaryExpression -> {
                if (expression.operationToken != KtTokens.PLUS) return null
                val left = fragment(expression.left, depth + 1) ?: return null
                val right = fragment(expression.right, depth + 1) ?: return null
                left + right
            }

            is KtStringTemplateExpression -> stringTemplate(expression)

            is KtCallExpression -> environmentBinding(expression)
            else -> null
        }
    }

    private fun stringTemplate(expression: KtStringTemplateExpression): String? = buildString {
        expression.entries.forEach { entry ->
            when (entry) {
                is KtLiteralStringTemplateEntry -> append(entry.text)
                is KtEscapeStringTemplateEntry -> append(entry.unescapedValue)
                is KtStringTemplateEntryWithExpression -> {
                    val name = entry.expression as? KtNameReferenceExpression ?: return null
                    append("\${").append(name.getReferencedName()).append('}')
                }

                else -> return null
            }
        }
    }

    /** Only the importer's two-statement delegate wrapper is treated as a displayable environment reference. */
    private fun environmentBinding(expression: KtCallExpression): String? {
        if (expression.calleeExpression?.text != "run" || expression.valueArgumentList?.arguments.orEmpty()
                .isNotEmpty() ||
            expression.typeArguments.isNotEmpty()
        ) return null
        val lambda = expression.lambdaArguments.singleOrNull()?.getLambdaExpression() ?: return null
        if (lambda.valueParameters.isNotEmpty()) return null
        val statements = lambda.bodyExpression?.statements ?: return null
        if (statements.size != 2) return null
        val property = statements[0] as? KtProperty ?: return null
        val reference = statements[1] as? KtNameReferenceExpression ?: return null
        val name = property.name ?: return null
        if (property.isVar || property.initializer != null || property.typeReference?.text != "String" ||
            property.delegateExpression?.text != "env" || reference.getReferencedName() != name ||
            property.receiverTypeReference != null || property.modifierList != null
        ) return null
        return "\${$name}"
    }
}
