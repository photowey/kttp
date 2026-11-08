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

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.source.tree.LeafPsiElement
import io.github.photowey.kttp.idea.substitutor.KttpLanguageSubstitutor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Body 的静态来源分类；此处不求值 Kotlin 表达式。
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/17
 */
sealed class BodySource {
    /**
     * Contains body text extracted from the request's PSI representation.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/17
     */
    data class Literal(val content: String) : BodySource()

    /**
     * Contains a local-file path recognized in a body value's delegate.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/17
     */
    data class LocalFile(val path: String) : BodySource()

    /**
     * Identifies an environment property referenced by a body value.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/17
     */
    data class Env(val varName: String) : BodySource()

    /**
     * Indicates that no supported body source was extracted.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/17
     */
    data object None : BodySource()
}

/**
 * HTTP 请求调用的 PSI 包装；辅助提取方法只匹配有限的语法，不执行脚本。
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/17
 */
class KttpRequestDeclaration(
    callExpression: KtCallExpression
) {
    private val callExpressionRef: SmartPsiElementPointer<KtCallExpression> =
        com.intellij.psi.SmartPointerManager.getInstance(callExpression.project)
            .createSmartPsiElementPointer(callExpression)

    val element: KtCallExpression?
        get() = callExpressionRef.element?.takeIf { it.isValid }

    /**
     * 按父级语法结构判断是否作为请求声明候选，不验证脚本能否编译或运行。
     */
    fun isRegistrable(): Boolean {
        val expr = element ?: return false

        // 检查父元素，确保不在其他请求构建器调用内部
        var parent = expr.parent
        while (parent != null) {
            when (parent) {
                is KtCallExpression -> {
                    // 如果在另一个请求构建器调用内部，则不可注册
                    if (isRequestBuilderCall(parent)) return false
                }

                is org.jetbrains.kotlin.psi.KtFunction,
                is org.jetbrains.kotlin.psi.KtClassOrObject -> return false
            }
            parent = parent.parent
        }
        return true
    }

    /**
     * 获取 HTTP 方法名
     */
    fun getHttpMethod(): String? {
        val expr = element ?: return null
        val callee = expr.calleeExpression as? KtNameReferenceExpression
        return callee?.getReferencedName()?.uppercase()
    }

    /**
     * 获取第一个参数的文本表示，不求值变量或字符串插值。
     */
    fun getUrl(): String? {
        val expr = element ?: return null
        val firstArg = expr.valueArguments.firstOrNull() ?: return null
        val argExpr = firstArg.getArgumentExpression() ?: return null
        return evaluateStringExpression(argExpr)
    }

    /**
     * 从第二个值参数中的 lambda 提取 header / headers 调用文本。
     * 不执行表达式；该参数不是 lambda 时返回空映射。
     */
    fun getHeaders(): Map<String, String> {
        val expr = element ?: return emptyMap()
        val headers = mutableMapOf<String, String>()

        // 查找 lambda 表达式（第二个参数）
        val lambdaArg = expr.valueArguments.getOrNull(1)?.getArgumentExpression()
            as? org.jetbrains.kotlin.psi.KtLambdaExpression ?: return emptyMap()

        // 遍历 lambda 体中的语句
        lambdaArg.bodyExpression?.statements?.forEach { statement ->
            if (statement is KtCallExpression) {
                val callee = statement.calleeExpression as? KtNameReferenceExpression ?: return@forEach
                val methodName = callee.getReferencedName()

                when (methodName) {
                    "header" -> {
                        // header("key", "value")
                        val keyArg = statement.valueArguments.getOrNull(0)?.getArgumentExpression()
                        val valueArg = statement.valueArguments.getOrNull(1)?.getArgumentExpression()
                        val key = keyArg?.let { evaluateStringExpression(it) } ?: return@forEach
                        val value = valueArg?.let { evaluateStringExpression(it) } ?: ""
                        headers[key] = value
                    }

                    "headers" -> {
                        // 仅匹配 PSI 中表示为 to 调用的参数，不处理通用 Map 表达式。
                        statement.valueArguments.forEach { arg ->
                            val argExpr = arg.getArgumentExpression() ?: return@forEach
                            if (argExpr is KtCallExpression) {
                                val toCallee = argExpr.calleeExpression as? KtNameReferenceExpression
                                if (toCallee?.getReferencedName() == "to") {
                                    val keyArg = argExpr.valueArguments.getOrNull(0)?.getArgumentExpression()
                                    val valueArg = argExpr.valueArguments.getOrNull(1)?.getArgumentExpression()
                                    val key = keyArg?.let { evaluateStringExpression(it) } ?: return@forEach
                                    val value = valueArg?.let { evaluateStringExpression(it) } ?: ""
                                    headers[key] = value
                                }
                            }
                        }
                    }
                }
            }
        }

        return headers
    }

    /**
     * 从第二个值参数中的 lambda 提取 queryParam / query 调用文本。
     * 不执行表达式；该参数不是 lambda 时返回空映射。
     */
    fun getQueryParams(): Map<String, String> {
        val expr = element ?: return emptyMap()
        val params = mutableMapOf<String, String>()

        // 查找 lambda 表达式（第二个参数）
        val lambdaArg = expr.valueArguments.getOrNull(1)?.getArgumentExpression()
            as? org.jetbrains.kotlin.psi.KtLambdaExpression ?: return emptyMap()

        // 遍历 lambda 体中的语句
        lambdaArg.bodyExpression?.statements?.forEach { statement ->
            if (statement is KtCallExpression) {
                val callee = statement.calleeExpression as? KtNameReferenceExpression ?: return@forEach
                val methodName = callee.getReferencedName()

                when (methodName) {
                    "queryParam" -> {
                        // queryParam("key", "value")
                        val keyArg = statement.valueArguments.getOrNull(0)?.getArgumentExpression()
                        val valueArg = statement.valueArguments.getOrNull(1)?.getArgumentExpression()
                        val key = keyArg?.let { evaluateStringExpression(it) } ?: return@forEach
                        val value = valueArg?.let { evaluateStringExpression(it) } ?: ""
                        params[key] = value
                    }

                    "query" -> {
                        // 两个及以上参数按键和值提取；单参数分支尝试匹配 to 调用。
                        if (statement.valueArguments.size >= 2) {
                            // query("key", "value")
                            val keyArg = statement.valueArguments.getOrNull(0)?.getArgumentExpression()
                            val valueArg = statement.valueArguments.getOrNull(1)?.getArgumentExpression()
                            val key = keyArg?.let { evaluateStringExpression(it) } ?: return@forEach
                            val value = valueArg?.let { evaluateStringExpression(it) } ?: ""
                            params[key] = value
                        } else {
                            // 仅匹配 PSI 中表示为 to 调用的参数。
                            statement.valueArguments.forEach { arg ->
                                val argExpr = arg.getArgumentExpression() ?: return@forEach
                                if (argExpr is KtCallExpression) {
                                    val toCallee = argExpr.calleeExpression as? KtNameReferenceExpression
                                    if (toCallee?.getReferencedName() == "to") {
                                        val keyArg = argExpr.valueArguments.getOrNull(0)?.getArgumentExpression()
                                        val valueArg = argExpr.valueArguments.getOrNull(1)?.getArgumentExpression()
                                        val key = keyArg?.let { evaluateStringExpression(it) } ?: return@forEach
                                        val value = valueArg?.let { evaluateStringExpression(it) } ?: ""
                                        params[key] = value
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return params
    }

    /**
     * 返回 [getBodySource] 标记为 Literal 的文本，不对表达式求值。
     */
    fun getBody(): String? {
        return when (val src = getBodySource(null)) {
            is BodySource.Literal -> src.content
            else -> null
        }
    }

    /**
     * 从第二个值参数中的 lambda 提取 Body 来源：文本、localFile 路径、环境变量名、或无。
     * 当 body() 参数为变量时，根据同文件内该属性的委托（localFile / env）返回对应来源。
     * 返回的文本不会展开 Kotlin 插值或执行表达式。
     *
     * @param ktFile 用于查找 body(varName) 对应的变量委托；为 null 时不追踪变量。
     */
    fun getBodySource(ktFile: KtFile?): BodySource {
        val expr = element ?: return BodySource.None
        val lambdaArg = expr.valueArguments.getOrNull(1)?.getArgumentExpression()
            as? org.jetbrains.kotlin.psi.KtLambdaExpression ?: return BodySource.None

        var bodyArg: KtExpression? = null
        lambdaArg.bodyExpression?.statements?.forEach { statement ->
            if (statement is KtCallExpression) {
                val callee = statement.calleeExpression as? KtNameReferenceExpression ?: return@forEach
                if (callee.getReferencedName() == "body") {
                    bodyArg = statement.valueArguments.firstOrNull()?.getArgumentExpression() as? KtExpression
                    return@forEach
                }
            }
        } ?: return BodySource.None

        val arg = bodyArg ?: return BodySource.None
        return when (arg) {
            is KtStringTemplateExpression,
            is KtConstantExpression -> {
                evaluateStringExpression(arg)?.let { BodySource.Literal(it) } ?: BodySource.None
            }

            is KtNameReferenceExpression -> {
                val varName = arg.getReferencedName() ?: return BodySource.None
                if (ktFile == null) return BodySource.None
                findPropertyDelegate(ktFile, varName) ?: BodySource.None
            }

            else -> {
                // 其他表达式保留源码文本，不计算拼接或函数调用。
                evaluateStringExpression(arg)?.let { BodySource.Literal(it) } ?: BodySource.None
            }
        }
    }

    /**
     * 在脚本文件中查找同名属性的委托：若为 localFile("path") 或 env，返回对应 BodySource
     */
    private fun findPropertyDelegate(ktFile: KtFile, varName: String): BodySource? {
        var result: BodySource? = null
        ktFile.accept(object : com.intellij.psi.PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is KtProperty && element.name == varName) {
                    val delegateExpr = element.delegateExpression ?: return
                    when {
                        KttpLanguageSubstitutor.isLocalFileDelegate(element) -> {
                            val call = delegateExpr as? KtCallExpression ?: return
                            val path = KttpLanguageSubstitutor.getLocalFilePathArgument(call)
                                ?: (call.valueArguments.firstOrNull()?.getArgumentExpression()
                                    ?.let { evaluateStringExpression(it) })
                            if (!path.isNullOrBlank()) result = BodySource.LocalFile(path)
                        }

                        KttpLanguageSubstitutor.isEnvDelegate(element) -> {
                            result = BodySource.Env(element.name ?: varName)
                        }
                    }
                    return
                }
                element.acceptChildren(this)
            }
        })
        return result
    }

    /**
     * 提取 name / alias 参数文本；未指定时尝试使用委托属性名。
     */
    fun getName(): String? {
        val expr = element ?: return null

        // 查找 name 参数
        val nameArg = expr.valueArguments.find { arg ->
            val name = arg.getArgumentName()?.asName?.asString()
            name == "name" || name == "alias"
        }

        // 尝试获取字符串值
        nameArg?.getArgumentExpression()?.let { expr ->
            return evaluateStringExpression(expr)
        }

        // 尝试从委托属性名获取
        return getDelegatedPropertyName()
    }

    /**
     * 获取显示文本
     */
    fun presentableText(): String? {
        val method = getHttpMethod() ?: return null

        // 如果是 useCase 风格的调用，返回名称
        if (method == "USECASE") {
            return getName() ?: "<unnamed>"
        }

        // 优先返回请求名称；没有名称时返回方法名
        return getName() ?: method
    }

    private fun getDelegatedPropertyName(): String? {
        val expr = element ?: return null
        val parent = expr.parent
        if (parent is org.jetbrains.kotlin.psi.KtProperty) {
            return parent.name
        }
        return null
    }

    /**
     * Recognizes request call names and extracts expression text without evaluating Kotlin.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/17
     */
    companion object {
        private val HTTP_METHODS = setOf(
            "GET", "POST", "PUT", "DELETE", "PATCH",
            "HEAD", "OPTIONS", "TRACE", "REQUEST", "USECASE"
        )

        fun isRequestBuilderCall(callExpression: KtCallExpression): Boolean {
            val callee = callExpression.calleeExpression as? KtNameReferenceExpression
                ?: return false
            val name = callee.getReferencedName().uppercase()
            return name in HTTP_METHODS
        }

        fun evaluateStringExpression(expr: KtExpression): String? {
            // 提取 PSI 文本或移除两端双引号；不求值插值、转义、变量或拼接。
            return when (expr) {
                is KtStringTemplateExpression -> {
                    if (expr.entries.size == 1 && expr.entries[0] is org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry) {
                        (expr.entries[0] as org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry).text
                    } else {
                        expr.text?.removeSurrounding("\"", "\"")
                    }
                }

                is KtConstantExpression -> expr.text?.removeSurrounding("\"", "\"")
                else -> expr.text
            }
        }
    }
}

/**
 * 从 PSI 元素解析请求声明
 */
fun PsiElement.parseKttpRequestDeclaration(): KttpRequestDeclaration? {
    // 如果当前是叶子元素，向上查找
    if (this is LeafPsiElement) {
        val parent = this.parent
        if (parent is KtNameReferenceExpression) {
            val grandparent = parent.parent
            if (grandparent is KtCallExpression) {
                return grandparent.toKttpRequestDeclaration()
            }
        }
    }

    // 如果当前是 KtCallExpression
    if (this is KtCallExpression) {
        return this.toKttpRequestDeclaration()
    }

    return null
}

/**
 * 将 KtCallExpression 转换为请求声明
 */
fun KtCallExpression.toKttpRequestDeclaration(): KttpRequestDeclaration? {
    if (!KttpRequestDeclaration.isRequestBuilderCall(this)) return null
    return KttpRequestDeclaration(this)
}
