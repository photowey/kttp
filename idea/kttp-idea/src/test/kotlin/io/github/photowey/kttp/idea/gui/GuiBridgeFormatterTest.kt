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
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies projection-gap grouping and value-source summaries.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class GuiBridgeFormatterTest {

    @Test
    fun bucketGapsSeparatesBlockingAndAdvisory() {
        val buckets = GuiBridgeFormatter.bucketGaps(
            listOf(
                SemanticGap(GapKind.POST_PROCESSING_ONLY, "then", "skip then"),
                SemanticGap(GapKind.REQUEST_SHAPING_UNSUPPORTED, "configure", "unsupported")
            )
        )

        assertEquals(1, buckets.advisory.size)
        assertEquals(1, buckets.blocking.size)
    }

    @Test
    fun formatSourceSummaryCountsLiteralEnvAndFileSources() {
        val spec = RequestSpec(
            method = HttpMethod.POST,
            target = RequestTarget(
                url = ValueRef.Env("BASE_URL"),
                queryParams = listOf(NameValueRef("page", ValueRef.Literal("1")))
            ),
            headers = listOf(HeaderSpec("Authorization", ValueRef.Env("TOKEN"))),
            auth = AuthSpec.Bearer(ValueRef.Env("TOKEN")),
            body = BodySpec.Raw(ValueRef.LocalFile("payload.json"))
        )

        val summary = GuiBridgeFormatter.formatSourceSummary(spec)

        assertTrue(summary.contains("URL: 1 env"))
        assertTrue(summary.contains("Params: 1 literal"))
        assertTrue(summary.contains("Headers: 1 env"))
        assertTrue(summary.contains("Auth: 1 env"))
        assertTrue(summary.contains("Body: 1 file"))
    }
}
