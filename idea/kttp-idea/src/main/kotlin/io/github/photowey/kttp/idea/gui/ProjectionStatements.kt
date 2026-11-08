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

import io.github.photowey.kttp.idea.gui.ProjectionOuterSemantics.unsupportedGap
import io.github.photowey.kttp.idea.gui.ProjectionSyntax.bindArguments
import io.github.photowey.kttp.idea.gui.ProjectionSyntax.toValueRef
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * Collects request fields and semantic gaps extracted from a request builder block.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal data class ProjectedRequestContent(
    val headers: List<HeaderSpec>,
    val queryParams: List<NameValueRef>,
    val pathParams: List<NameValueRef>,
    val body: BodySpec,
    val auth: AuthSpec,
    val transport: TransportSpec,
    val gaps: List<SemanticGap>
)

/**
 * Projects one request's ordered statements without evaluating arbitrary Kotlin.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal class ProjectionStatements(private val ktFile: KtFile?) {
    private val headers = mutableListOf<HeaderSpec>()
    private val queryParams = mutableListOf<NameValueRef>()
    private val pathParams = mutableListOf<NameValueRef>()
    private val gaps = mutableListOf<SemanticGap>()
    private var body: BodySpec = BodySpec.None
    private var auth: AuthSpec = AuthSpec.Inherit
    private var authenticationCalls = 0
    private var transport = TransportSpec()

    fun parse(lambda: KtLambdaExpression?): ProjectedRequestContent {
        lambda?.bodyExpression?.statements.orEmpty().forEach(::consume)
        validateAuthentication()
        return ProjectedRequestContent(
            headers.toList(), queryParams.toList(), pathParams.toList(),
            projectBodyType(), auth, transport, gaps.toList()
        )
    }

    private fun consume(statement: KtExpression) {
        if (statement !is KtCallExpression) {
            gaps += unsupportedGap("body", "Request body contains unsupported Kotlin statements.")
            return
        }
        val callee = (statement.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
        if (callee == null) {
            gaps += unsupportedGap("body", "Request contains an unsupported callable expression.")
            return
        }
        when (callee) {
            "query", "queryParam" -> addNamedValue(statement, queryParams, "query")
            "path" -> addNamedValue(statement, pathParams, "path")
            "header" -> addHeader(statement)
            "headers" -> addHeaders(statement)
            "contentType" -> addContentHeader(statement, "Content-Type")
            "accept" -> addContentHeader(statement, "Accept")
            "bearerAuth" -> parseBearerAuth(statement)
            "basicAuth" -> parseBasicAuth(statement)
            "body" -> parseBody(statement)
            "formData" -> parseFormBody(statement)
            "noRedirect", "noCookies", "http2" -> parseTransport(statement, callee)
            else -> {
                gaps += ProjectionOuterSemantics.postProcessingGap(callee)
                    ?: unsupportedGap(callee, "Unsupported request DSL call '$callee'.")
            }
        }
    }

    private fun addNamedValue(call: KtCallExpression, target: MutableList<NameValueRef>, location: String) {
        val value = parseNamedValueCall(call, ktFile)
        if (value != null) target += value
        else gaps += unsupportedGap(
            location,
            "${location.replaceFirstChar { it.uppercase() }} parameter uses unsupported expression."
        )
    }

    private fun addHeader(call: KtCallExpression) {
        val header = parseHeaderCall(call, ktFile)
        if (header != null) headers += header
        else gaps += unsupportedGap("header", "Header uses unsupported expression.")
    }

    private fun addHeaders(call: KtCallExpression) {
        val parsed = parseHeadersCall(call, ktFile)
        if (parsed != null) headers += parsed
        else gaps += unsupportedGap("headers", "headers(...) is only supported for simple \"key to value\" lists.")
    }

    private fun addContentHeader(call: KtCallExpression, name: String) {
        val value = scalarArgument(call, "contentType")
        if (value != null) headers += HeaderSpec(name, value)
        else gaps += unsupportedGap(call.calleeExpression?.text.orEmpty(), "Content type uses unsupported expression.")
    }

    private fun parseBearerAuth(call: KtCallExpression) {
        authenticationCalls++
        val value = scalarArgument(call, "token")
        if (value != null) auth = AuthSpec.Bearer(value)
        else gaps += unsupportedGap("bearerAuth", "bearerAuth(...) uses unsupported expression.")
    }

    private fun parseBasicAuth(call: KtCallExpression) {
        authenticationCalls++
        val arguments = bindArguments(call, listOf("username", "password"))
        val username = arguments?.get("username")?.let(::scalarValue)
        val password = arguments?.get("password")?.let(::scalarValue)
        if (username != null && password != null) auth = AuthSpec.Basic(username, password)
        else gaps += unsupportedGap("basicAuth", "basicAuth(...) uses unsupported expression.")
    }

    private fun parseBody(call: KtCallExpression) {
        val value = bindArguments(call, listOf("body"))?.get("body")
            ?.let { toValueRef(it, ktFile, allowLocalFile = true) }
        if (value is ValueRef.Literal || value is ValueRef.Env || value is ValueRef.LocalFile) {
            body = BodySpec.Raw(value)
        } else gaps += unsupportedGap("body", "body(...) uses unsupported expression.")
    }

    private fun parseFormBody(call: KtCallExpression) {
        val fields = parseFormData(call, ktFile)
        if (fields != null) body = BodySpec.FormUrlEncoded(fields)
        else gaps += unsupportedGap("formData", "formData { ... } contains unsupported expressions.")
    }

    private fun parseTransport(call: KtCallExpression, callee: String) {
        if (bindArguments(call, emptyList()) == null) {
            gaps += unsupportedGap(callee, "Transport call '$callee' does not accept arguments.")
            return
        }
        transport = when (callee) {
            "noRedirect" -> transport.copy(noRedirect = true)
            "noCookies" -> transport.copy(noCookies = true)
            else -> transport.copy(http2PriorKnowledge = true)
        }
    }

    private fun scalarArgument(call: KtCallExpression, parameter: String): ValueRef? =
        bindArguments(call, listOf(parameter))?.get(parameter)?.let(::scalarValue)

    private fun scalarValue(expression: KtExpression): ValueRef? =
        toValueRef(expression, ktFile, allowLocalFile = false)
            .takeIf { it is ValueRef.Literal || it is ValueRef.Env }

    private fun validateAuthentication() {
        // GUI Send renders AuthSpec after ordinary headers, so these sources cannot
        // preserve script ordering if authentication is repeated or mixed with a header.
        if (authenticationCalls > 1 ||
            (auth !is AuthSpec.Inherit && headers.any { it.name.equals("Authorization", ignoreCase = true) })
        ) {
            gaps += unsupportedGap(
                "auth",
                "Repeated authentication or mixed Authorization sources require script execution to preserve their order."
            )
        }
    }

    private fun projectBodyType(): BodySpec {
        val rawBody = body as? BodySpec.Raw ?: return body
        val contentType = headers.lastOrNull { it.name.equals("Content-Type", ignoreCase = true) }
            ?.value as? ValueRef.Literal ?: return body
        val mediaType = contentType.value.substringBefore(';').trim()
        return if (mediaType.equals("application/json", ignoreCase = true) || mediaType.endsWith(
                "+json",
                ignoreCase = true
            )
        ) {
            BodySpec.Json(rawBody.content)
        } else body
    }

    private fun parseHeaderCall(statement: KtCallExpression, ktFile: KtFile?): HeaderSpec? {
        val arguments = bindArguments(statement, listOf("key", "value")) ?: return null
        val nameExpr = arguments["key"] ?: return null
        val valueExpr = arguments["value"] ?: return null
        val name = literalName(nameExpr, ktFile) ?: return null
        val value = toValueRef(valueExpr, ktFile, allowLocalFile = false)
        return if (value is ValueRef.Literal || value is ValueRef.Env) HeaderSpec(name, value) else null
    }

    private fun parseNamedValueCall(statement: KtCallExpression, ktFile: KtFile?): NameValueRef? {
        val keyParameter = if (statement.calleeExpression?.text == "field") "name" else "key"
        val arguments = bindArguments(statement, listOf(keyParameter, "value")) ?: return null
        val keyExpr = arguments[keyParameter] ?: return null
        val valueExpr = arguments["value"] ?: return null
        val key = literalName(keyExpr, ktFile) ?: return null
        val value = toValueRef(valueExpr, ktFile, allowLocalFile = false)
        return if (value is ValueRef.Literal || value is ValueRef.Env) NameValueRef(key, value) else null
    }

    private fun parseHeadersCall(statement: KtCallExpression, ktFile: KtFile?): List<HeaderSpec>? {
        val headers = mutableListOf<HeaderSpec>()
        for (arg in statement.valueArguments) {
            if (arg.isNamed() || arg.getSpreadElement() != null) return null
            val pair = arg.getArgumentExpression() as? KtBinaryExpression ?: return null
            if (pair.operationReference.getReferencedName() != "to") return null
            val nameExpr = pair.left ?: return null
            val valueExpr = pair.right ?: return null
            val name = literalName(nameExpr, ktFile) ?: return null
            val value = toValueRef(valueExpr, ktFile, allowLocalFile = false)
            if (value !is ValueRef.Literal && value !is ValueRef.Env) return null
            headers += HeaderSpec(name, value)
        }
        return headers
    }

    private fun parseFormData(statement: KtCallExpression, ktFile: KtFile?): List<NameValueRef>? {
        val arguments = bindArguments(statement, listOf("block"), trailingLambdaParameter = "block") ?: return null
        val lambda = arguments["block"] as? KtLambdaExpression ?: return null

        val fields = mutableListOf<NameValueRef>()
        for (fieldCall in lambda.bodyExpression?.statements.orEmpty()) {
            val call = fieldCall as? KtCallExpression ?: return null
            val callee = (call.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
            if (callee != "field") return null
            val pair = parseNamedValueCall(call, ktFile) ?: return null
            fields += pair
        }
        return fields
    }

    private fun literalName(expr: KtExpression, ktFile: KtFile?): String? =
        (toValueRef(expr, ktFile, allowLocalFile = false) as? ValueRef.Literal)
            ?.value?.takeIf { it.isNotBlank() }

}
