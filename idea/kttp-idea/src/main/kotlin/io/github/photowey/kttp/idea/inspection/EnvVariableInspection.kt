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

package io.github.photowey.kttp.idea.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import io.github.photowey.kttp.idea.lang.KttpFileType
import io.github.photowey.kttp.idea.substitutor.KttpLanguageSubstitutor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty

/**
 * 对 .kttp.kts 中匹配 env 委托语法的属性统一提示“可能未设置”。
 * 不读取当前选定的环境，也不判断变量是否实际缺失。
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class EnvVariableInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is KtProperty) {
                    val file = element.containingFile as? KtFile ?: return
                    if (file.name.endsWith("." + KttpFileType.INSTANCE.defaultExtension) &&
                        KttpLanguageSubstitutor.isEnvDelegate(element)
                    ) {
                        val delegateExpr = element.delegateExpression ?: return
                        holder.registerProblem(
                            delegateExpr,
                            "Environment variable \"${element.name}\" might not be set at runtime.",
                            com.intellij.codeInspection.ProblemHighlightType.WEAK_WARNING
                        )
                    }
                }
                element.acceptChildren(this)
            }
        }
    }
}
