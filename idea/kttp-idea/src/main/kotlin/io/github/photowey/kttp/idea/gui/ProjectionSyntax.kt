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

import io.github.photowey.kttp.core.enums.HttpMethod
import io.github.photowey.kttp.idea.script.KttpRequestDeclaration
import io.github.photowey.kttp.idea.substitutor.KttpLanguageSubstitutor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import java.nio.charset.Charset

/**
 * Static DSL argument and value parsing; never evaluates Kotlin expressions.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal object ProjectionSyntax {
    private val guiReadyMethods = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

    /**
     * Contains the HTTP method, URL source, name, and builder block recognized in a request call.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    data class RequestSignature(
        val method: HttpMethod?,
        val urlRef: ValueRef,
        val lambda: KtLambdaExpression?,
        val customRequestEntry: Boolean = false,
        val name: String? = null,
        val argumentError: String? = null
    )

    fun extractRequestSignature(element: KtCallExpression, declaration: KttpRequestDeclaration): RequestSignature {
        val methodName = declaration.getHttpMethod()?.uppercase()
        val customRequest = methodName == "REQUEST"
        fun unsupportedArguments(message: String) = RequestSignature(
            method = null, urlRef = ValueRef.OpaqueScript(element.text), lambda = null,
            customRequestEntry = customRequest, argumentError = message
        )

        val parameters =
            if (customRequest) listOf("method", "path", "name", "configure") else listOf("path", "name", "configure")
        val arguments = bindArguments(element, parameters, trailingLambdaParameter = "configure")
            ?: return unsupportedArguments("Request arguments cannot be mapped safely to the DSL parameters.")
        val configure = arguments["configure"]
        if (configure != null && configure !is KtLambdaExpression) {
            return unsupportedArguments("Request configuration must be an inline lambda to be projected safely.")
        }
        val file = element.containingFile as? KtFile
        val nameExpression = arguments["name"]
        val name = when {
            nameExpression == null -> declaration.getName()
            nameExpression is KtConstantExpression && nameExpression.text == "null" -> null
            else -> (toValueRef(nameExpression, file, allowLocalFile = false) as? ValueRef.Literal)?.value
                ?: return unsupportedArguments("Request name uses an unsupported Kotlin expression.")
        }
        val urlRef =
            arguments["path"]?.let { toValueRef(it, file, allowLocalFile = false) } ?: ValueRef.OpaqueScript("")
        if (customRequest) {
            val methodLiteral =
                (arguments["method"] as? KtStringTemplateExpression)?.let(::literalStringValue)?.uppercase()
            val method = methodLiteral?.takeIf { it in guiReadyMethods }?.let(HttpMethod::valueOf)
            return RequestSignature(
                method = method,
                urlRef = urlRef,
                lambda = configure as? KtLambdaExpression,
                customRequestEntry = true,
                name = name
            )
        }

        val method = methodName?.takeIf { it in guiReadyMethods }?.let(HttpMethod::valueOf)
        return RequestSignature(
            method = method,
            urlRef = urlRef,
            lambda = configure as? KtLambdaExpression,
            name = name
        )
    }

    /** Bind only known DSL parameters; never infer a positional meaning for an unknown named argument. */
    fun bindArguments(
        call: KtCallExpression,
        parameters: List<String>,
        trailingLambdaParameter: String? = null
    ): Map<String, KtExpression>? {
        val arguments = linkedMapOf<String, KtExpression>()
        var positionalIndex = 0
        var seenNamed = false
        for (argument in call.valueArgumentList?.arguments.orEmpty()) {
            val explicitName = argument.getArgumentName()?.asName?.asString()
            if (explicitName != null) seenNamed = true
            else if (seenNamed) return null
            val name = explicitName ?: parameters.getOrNull(positionalIndex++) ?: return null
            if (name !in parameters || name in arguments || argument.getSpreadElement() != null) return null
            arguments[name] = argument.getArgumentExpression() ?: return null
        }
        if (call.lambdaArguments.isNotEmpty()) {
            if (call.lambdaArguments.size != 1 || trailingLambdaParameter == null || trailingLambdaParameter in arguments) return null
            arguments[trailingLambdaParameter] = call.lambdaArguments.single().getLambdaExpression() ?: return null
        }
        return arguments
    }

    /** Read static Kotlin string entries without evaluating any template expressions. */
    private fun literalStringValue(expr: KtStringTemplateExpression): String? {
        val source = expr.text
        val quote = if (source.startsWith("\"\"\"")) "\"\"\"" else "\""
        if (source.length < quote.length * 2 || !source.startsWith(quote) || !source.endsWith(quote)) return null
        return buildString {
            for (entry in expr.entries) {
                when (entry) {
                    is KtLiteralStringTemplateEntry -> append(entry.text)
                    is KtEscapeStringTemplateEntry -> {
                        val escape = entry.text
                        val valid = (escape.length == 2 && escape[1] in "tbnr'\"\\${'$'}") ||
                            (escape.length == 6 && escape.startsWith("\\u") && escape.drop(2)
                                .all { it.digitToIntOrNull(16) != null })
                        if (!valid) return null
                        append(entry.unescapedValue)
                    }

                    else -> return null
                }
            }
        }
    }

    private fun localFileValue(call: KtCallExpression): ValueRef.LocalFile? {
        val arguments = bindArguments(call, listOf("path", "charset", "required")) ?: return null
        val path = (arguments["path"] as? KtStringTemplateExpression)?.let(::literalStringValue)
            ?.takeIf { it.isNotBlank() } ?: return null
        val charset = arguments["charset"]?.let { charsetName(it) ?: return null } ?: "UTF-8"
        val required = when (arguments["required"]?.text) {
            null, "true" -> true
            "false" -> false
            else -> return null
        }
        return ValueRef.LocalFile(path, charset, required)
    }

    private fun charsetName(expr: KtExpression): String? {
        val qualified = expr as? KtDotQualifiedExpression ?: return null
        val receiver = qualified.receiverExpression.text
        val selector = qualified.selectorExpression
        val name = when {
            receiver in setOf("java.nio.charset.Charset", "Charset") && selector is KtCallExpression -> {
                if (selector.calleeExpression?.text != "forName" || selector.valueArguments.size != 1) return null
                (selector.valueArguments.single().getArgumentExpression() as? KtStringTemplateExpression)
                    ?.let(::literalStringValue) ?: return null
            }

            receiver in setOf(
                "java.nio.charset.StandardCharsets",
                "StandardCharsets",
                "kotlin.text.Charsets",
                "Charsets"
            ) &&
                selector is KtNameReferenceExpression -> selector.getReferencedName().replace('_', '-')

            else -> return null
        }
        return runCatching { Charset.forName(name).name() }.getOrNull()
    }

    fun toValueRef(expr: KtExpression, ktFile: KtFile?, allowLocalFile: Boolean): ValueRef {
        return when (expr) {
            is KtStringTemplateExpression -> {
                literalStringValue(expr)?.let { ValueRef.Literal(it) } ?: ValueRef.OpaqueScript(expr.text)
            }

            is KtConstantExpression -> ValueRef.Literal(KttpRequestDeclaration.evaluateStringExpression(expr).orEmpty())

            is KtNameReferenceExpression -> {
                val name = expr.getReferencedName().orEmpty()
                if (ktFile != null) {
                    findPropertyValueRef(ktFile, name, allowLocalFile) ?: ValueRef.OpaqueScript(expr.text)
                } else {
                    ValueRef.OpaqueScript(expr.text)
                }
            }

            is KtCallExpression -> {
                val callee = (expr.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
                if (allowLocalFile && callee == KttpLanguageSubstitutor.LOCAL_FILE_FUNCTION_NAME) {
                    localFileValue(expr) ?: ValueRef.OpaqueScript(expr.text)
                } else {
                    ValueRef.OpaqueScript(expr.text)
                }
            }

            else -> ValueRef.OpaqueScript(expr.text)
        }
    }

    private fun findPropertyValueRef(
        ktFile: KtFile,
        name: String,
        allowLocalFile: Boolean,
        visited: Set<String> = emptySet()
    ): ValueRef? {
        if (name in visited) return null
        val declarations = mutableListOf<KtNamedDeclaration>()
        ktFile.accept(object : org.jetbrains.kotlin.psi.KtTreeVisitorVoid() {
            override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                if (declaration.name == name) declarations += declaration
                super.visitNamedDeclaration(declaration)
            }
        })
        val property = declarations.singleOrNull() as? KtProperty ?: return null
        val topLevel = property.parent == ktFile || property.parent == ktFile.script?.blockExpression
        if (!topLevel || property.isVar || property.getter != null || property.setter != null) return null

        return when {
            KttpLanguageSubstitutor.isEnvDelegate(property) -> ValueRef.Env(name)
            allowLocalFile && KttpLanguageSubstitutor.isLocalFileDelegate(property) -> {
                val delegate = property.delegateExpression as? KtCallExpression ?: return null
                localFileValue(delegate)
            }

            else -> when (val initializer = property.initializer) {
                is KtStringTemplateExpression -> literalStringValue(initializer)?.let { ValueRef.Literal(it) }
                is KtConstantExpression -> ValueRef.Literal(
                    KttpRequestDeclaration.evaluateStringExpression(initializer).orEmpty()
                )

                is KtNameReferenceExpression -> findPropertyValueRef(
                    ktFile = ktFile,
                    name = initializer.getReferencedName().orEmpty(),
                    allowLocalFile = allowLocalFile,
                    visited = visited + name
                )

                else -> null
            }
        }
    }
}
