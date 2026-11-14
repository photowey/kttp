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

package io.github.photowey.kttp.idea.importing

import org.junit.Test
import kotlin.test.*

/**
 * Checks resource limits against compact alias graphs and preserves missing-reference semantics.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/14
 */
class ImportVariableExpansionTest {
    @Test
    fun compactDoublingGraphCannotMaterializeAnOversizedValue() {
        val values = mutableMapOf("v0" to "x")
        (1..21).forEach { values["v$it"] = "{{v${it - 1}}}{{v${it - 1}}}" }
        assertFailsWith<IllegalArgumentException> {
            ImportVariableExpansion(values, false).resolve("v21")
        }
    }

    @Test
    fun sharedAliasesDoNotRepeatExponentialResolutionWork() {
        val values = mutableMapOf("v0" to "x")
        (1..16).forEach { values["v$it"] = "{{v${it - 1}}}{{v${it - 1}}}" }
        val expansion = ImportVariableExpansion(values, false)
        assertEquals("x".repeat(65_536), expansion.resolve("v16"))
        assertEquals("x".repeat(65_536), expansion.resolve("v16"))
    }

    @Test
    fun aggregateProfilesCannotExceedTheCombinedOutputBudget() {
        val value = "x".repeat(1_048_576)
        val bundle = ImportBundle(environments = (1..9).map {
            ImportedEnvironment("env$it", mapOf("value" to value))
        })
        assertFailsWith<IllegalArgumentException> { ImportEnvironmentWriter.merge(null, bundle) }
    }

    @Test
    fun replacementWorkAndRecursionHaveIndependentLimits() {
        assertFailsWith<IllegalArgumentException> {
            ImportVariableExpansion(emptyMap(), true).expand("{{x}}".repeat(10_001))
        }
        val values = (0..65).associate { "v$it" to "{{v${it + 1}}}" }
        assertNull(ImportVariableExpansion(values, false).resolve("v0"))
    }

    @Test
    fun missingReferencesStayVisibleOnlyInRequestLocalValues() {
        val values = mapOf("url" to "https://{{host}}/echo", "cycle" to "{{cycle}}")
        assertEquals("https://{{host}}/echo", ImportVariableExpansion(values, true).resolve("url"))
        assertNull(ImportVariableExpansion(values, false).resolve("url"))
        assertNull(ImportVariableExpansion(values, true).resolve("cycle"))
    }
}
