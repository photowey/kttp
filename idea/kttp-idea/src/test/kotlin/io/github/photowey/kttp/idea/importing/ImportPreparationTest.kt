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
 * Keeps requests that cannot be generated from blocking valid sibling selection.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
class ImportPreparationTest {
    @Test
    fun conversionErrorsDisableOnlyAffectedRows() {
        val requests = listOf(
            ImportedRequest("Invalid key", "GET", ImportValue("{{base.url}}", true)),
            ImportedRequest("Cyclic local", "GET", ImportValue("{{a}}", true), variables = mapOf("a" to "{{a}}")),
            ImportedRequest(
                "Relative upload",
                "POST",
                ImportValue("https://example.com"),
                body = ImportBody.File(ImportValue("a.bin"))
            ),
            ImportedRequest(
                "GET body",
                "GET",
                ImportValue("https://example.com"),
                body = ImportBody.Text(ImportValue("data"))
            ),
            ImportedRequest("Valid", "GET", ImportValue("https://example.com"))
        )
        val reviewed = ImportPreparation.review(ImportBundle(requests = requests), null)
        assertTrue(reviewed.diagnostics.isEmpty())
        reviewed.requests.take(4).forEach { assertFalse(it.selectable, it.name) }
        assertTrue(reviewed.requests.last().selectedByDefault)
    }

    @Test
    fun existingScriptPropertyConflictsRemainPerRowDiagnostics() {
        val bundle = ImportBundle(
            requests = listOf(
                ImportedRequest("Conflict", "GET", ImportValue("{{host}}", true)),
                ImportedRequest("Valid", "GET", ImportValue("https://example.com"))
            )
        )
        val reviewed = ImportPreparation.review(bundle, null, "val host = 42\n")
        assertFalse(reviewed.requests.first().selectable)
        assertTrue(reviewed.requests.last().selectedByDefault)
    }

}
