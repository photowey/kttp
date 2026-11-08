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

package io.github.photowey.kttp.idea.substitutor

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReferenceExpression

/**
 * 按 PSI 表达式名称识别 .kttp.kts 中的 env / localFile 委托，供补全、检查、文档、引用和 GUI 投影使用。
 * 此类只提供语法辅助；平台的 Kotlin 语言替换由
 * [io.github.photowey.kttp.idea.lang.KttpLanguageSubstitutor] 完成。
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpLanguageSubstitutor {

    /**
     * Provides PSI name checks and text extraction for environment and local-file delegates.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    companion object {
        const val ENV_DELEGATE_NAME = "env"
        const val LOCAL_FILE_FUNCTION_NAME = "localFile"

        /** 判断属性是否为 by env 委托（委托表达式为 "env" 或 env() 调用）。 */
        fun isEnvDelegate(property: KtProperty): Boolean {
            val delegateExpr = property.delegateExpression ?: return false
            return when (delegateExpr) {
                is KtReferenceExpression -> delegateExpr.text == ENV_DELEGATE_NAME
                is KtCallExpression -> (delegateExpr.calleeExpression as? KtReferenceExpression)?.text == ENV_DELEGATE_NAME
                else -> false
            }
        }

        /** 判断属性是否为 by localFile("...") 委托。 */
        fun isLocalFileDelegate(property: KtProperty): Boolean {
            val delegateExpr = property.delegateExpression as? KtCallExpression ?: return false
            return (delegateExpr.calleeExpression as? KtReferenceExpression)?.text == LOCAL_FILE_FUNCTION_NAME
        }

        /** 收集文件中所有 by env / by localFile 的属性名，用于补全。 */
        fun collectDelegateVariableNames(ktFile: org.jetbrains.kotlin.psi.KtFile): List<String> {
            val names = mutableSetOf<String>()
            ktFile.accept(object : com.intellij.psi.PsiElementVisitor() {
                override fun visitElement(element: com.intellij.psi.PsiElement) {
                    if (element is KtProperty) {
                        val name = element.name ?: return
                        if (isEnvDelegate(element) || isLocalFileDelegate(element)) names.add(name)
                    }
                    element.acceptChildren(this)
                }
            })
            return names.toList().sorted()
        }

        /** 判断调用是否为 localFile("...") 。 */
        fun isLocalFileCall(element: PsiElement): Boolean {
            val call = element.parentOfType<KtCallExpression>() ?: return false
            return (call.calleeExpression as? KtReferenceExpression)?.text == LOCAL_FILE_FUNCTION_NAME
        }

        /** 返回首个参数的源码并裁去两端双引号；不求值变量、字符串插值或转义序列。 */
        fun getLocalFilePathArgument(call: KtCallExpression): String? {
            val arg = call.valueArguments.firstOrNull() ?: return null
            return (arg.getArgumentExpression()?.text?.trim('"'))?.takeIf { it.isNotEmpty() }
        }
    }
}
