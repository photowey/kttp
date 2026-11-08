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

/**
 * Formats value sources and semantic projection gaps for the script bridge UI.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
object GuiBridgeFormatter {

    /**
     * Counts literal, environment, file, and opaque-script value sources.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    data class SourceKinds(
        val literal: Int = 0,
        val env: Int = 0,
        val file: Int = 0,
        val opaque: Int = 0
    ) {
        operator fun plus(other: SourceKinds): SourceKinds {
            return SourceKinds(
                literal = literal + other.literal,
                env = env + other.env,
                file = file + other.file,
                opaque = opaque + other.opaque
            )
        }

        fun isEmpty(): Boolean = literal == 0 && env == 0 && file == 0 && opaque == 0
    }

    /**
     * Separates projection gaps into blocking and advisory groups.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    data class GapBuckets(
        val blocking: List<SemanticGap>,
        val advisory: List<SemanticGap>
    )

    fun bucketGaps(gaps: List<SemanticGap>): GapBuckets {
        val (advisory, blocking) = gaps.partition {
            it.kind == GapKind.POST_PROCESSING_ONLY || it.kind == GapKind.PARTIALLY_PROJECTED
        }
        return GapBuckets(blocking = blocking, advisory = advisory)
    }

    fun formatSourceSummary(spec: RequestSpec): String {
        val sections = linkedMapOf(
            "URL" to summarize(spec.target.url),
            "Params" to spec.target.queryParams.fold(SourceKinds()) { acc, item -> acc + summarize(item.value) },
            "Headers" to spec.headers.fold(SourceKinds()) { acc, item -> acc + summarize(item.value) },
            "Auth" to summarize(spec.auth),
            "Body" to summarize(spec.body)
        )

        return sections.entries.joinToString("\n") { (label, summary) ->
            "$label: ${formatKinds(summary)}"
        }
    }

    fun formatGaps(gaps: List<SemanticGap>, emptyText: String): String {
        if (gaps.isEmpty()) return emptyText
        return gaps.joinToString("\n\n") { gap ->
            buildString {
                append(gap.kind.name.replace('_', ' '))
                gap.location?.let { append(" @ ").append(it) }
                append('\n')
                append(gap.message)
            }
        }
    }

    private fun summarize(body: BodySpec): SourceKinds {
        return when (body) {
            is BodySpec.None -> SourceKinds()
            is BodySpec.Json -> summarize(body.content)
            is BodySpec.Raw -> summarize(body.content) + (body.contentType?.let(::summarize) ?: SourceKinds())
            is BodySpec.FormUrlEncoded -> body.fields.fold(SourceKinds()) { acc, item -> acc + summarize(item.value) }
        }
    }

    private fun summarize(auth: AuthSpec): SourceKinds {
        return when (auth) {
            is AuthSpec.Inherit -> SourceKinds()
            is AuthSpec.Bearer -> summarize(auth.token)
            is AuthSpec.Basic -> summarize(auth.username) + summarize(auth.password)
            is AuthSpec.ApiKey -> summarize(auth.keyValue)
        }
    }

    private fun summarize(valueRef: ValueRef): SourceKinds {
        return when (valueRef) {
            is ValueRef.Literal -> SourceKinds(literal = 1)
            is ValueRef.Env -> SourceKinds(env = 1)
            is ValueRef.LocalFile -> SourceKinds(file = 1)
            is ValueRef.OpaqueScript -> SourceKinds(opaque = 1)
        }
    }

    private fun formatKinds(sourceKinds: SourceKinds): String {
        if (sourceKinds.isEmpty()) return "None"
        return buildList {
            if (sourceKinds.literal > 0) add("${sourceKinds.literal} literal")
            if (sourceKinds.env > 0) add("${sourceKinds.env} env")
            if (sourceKinds.file > 0) add("${sourceKinds.file} file")
            if (sourceKinds.opaque > 0) add("${sourceKinds.opaque} script-only")
        }.joinToString(", ")
    }
}
