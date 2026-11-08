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
import io.github.photowey.kttp.idea.gui.ProjectionSyntax.RequestSignature
import io.github.photowey.kttp.idea.script.KttpRequestDeclaration
import org.jetbrains.kotlin.psi.KtFile

/**
 * Pairs a projected request specification with its capability classification and semantic gaps.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
data class RequestProjection(
    val spec: RequestSpec?,
    val mode: GuiMode,
    val gaps: List<SemanticGap>
)

/**
 * Performs static PSI projection and records unsupported semantics without executing Kotlin.
 * Its capability classification controls editing/generation and Quick Request availability, not script executability.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
object RequestProjectionExtractor {

    /**
     * Statically projects recognized request syntax without executing Kotlin or resolving arbitrary expressions.
     * Blocking semantic gaps remove the request specification; advisory gaps retain a limited projection.
     */
    fun extract(
        declaration: KttpRequestDeclaration,
        ktFile: KtFile?,
        declarationIndex: Int?,
        sourceFilePath: String?
    ): RequestProjection {
        val element = declaration.element ?: return RequestProjection(
            spec = null,
            mode = GuiMode.SCRIPT_ONLY,
            gaps = listOf(SemanticGap(GapKind.SCRIPT_ONLY_FEATURE, message = "Request declaration is no longer valid."))
        )
        val signature = ProjectionSyntax.extractRequestSignature(element, declaration)
        val gaps = signatureGaps(signature).toMutableList()
        if (hasBlockingGap(gaps)) return RequestProjection(null, GuiMode.SCRIPT_ONLY, gaps)

        val content = ProjectionStatements(ktFile).parse(signature.lambda)
        gaps += ProjectionOuterSemantics.extract(element, ktFile)
        gaps += content.gaps
        val spec = RequestSpec(
            identity = RequestIdentity(
                name = signature.name,
                sourceFile = sourceFilePath,
                declarationIndex = declarationIndex
            ),
            method = requireNotNull(signature.method),
            target = RequestTarget(signature.urlRef, content.pathParams, content.queryParams),
            headers = content.headers,
            auth = content.auth,
            body = content.body,
            transport = content.transport
        ).takeUnless { hasBlockingGap(gaps) }
        return RequestProjection(
            spec, determineMode(spec, gaps),
            gaps.distinctBy { Triple(it.kind, it.location, it.message) }
        )
    }

    private fun signatureGaps(signature: RequestSignature): List<SemanticGap> {
        signature.argumentError?.let { return listOf(unsupportedGap("arguments", it)) }
        if (signature.method == null) {
            return listOf(
                SemanticGap(
                    GapKind.SCRIPT_ONLY_FEATURE, "method", "Request method is not supported by the GUI composer."
                )
            )
        }
        val gaps = mutableListOf<SemanticGap>()
        if (signature.customRequestEntry) {
            gaps += SemanticGap(
                GapKind.PARTIALLY_PROJECTED, "request", "Custom request(...) entrypoint is imported as Send Only."
            )
        }
        if (signature.urlRef !is ValueRef.Literal && signature.urlRef !is ValueRef.Env) {
            gaps += SemanticGap(
                GapKind.SCRIPT_EXPRESSION_UNSUPPORTED, "url", "URL uses an unsupported Kotlin expression."
            )
        }
        return gaps
    }

    internal fun determineMode(spec: RequestSpec?, gaps: List<SemanticGap>): GuiMode {
        if (spec == null || hasBlockingGap(gaps)) return GuiMode.SCRIPT_ONLY
        return if (gaps.isNotEmpty()) GuiMode.SEND_ONLY else GuiMode.GUI_READY
    }

    private fun hasBlockingGap(gaps: List<SemanticGap>): Boolean =
        gaps.any { it.kind != GapKind.POST_PROCESSING_ONLY && it.kind != GapKind.PARTIALLY_PROJECTED }
}
