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

import io.github.photowey.kttp.idea.importing.ui.ImportDialogState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies that only current, explicitly selected previews can be committed.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
class ImportDialogStateTest {
    private fun request(consent: Boolean = false, error: Boolean = false) = ImportedRequest(
        "Read", "GET", ImportValue("https://example.com"), diagnostics = when {
            error -> listOf(ImportDiagnostic(ImportSeverity.ERROR, "request", "Unsupported body"))
            consent -> listOf(ImportDiagnostic(ImportSeverity.WARNING, "request", "Review script", true))
            else -> emptyList()
        }
    )

    @Test
    fun importRequiresCompletedPreview() {
        val state = ImportDialogState()
        assertFalse(state.canImport)
        val token = state.beginPreview()
        assertFalse(state.canImport)
        assertTrue(state.acceptPreview(token, ImportBundle(requests = listOf(request()))))
        assertTrue(state.canImport)
    }

    @Test
    fun inputOrFormatOrTargetChangeRejectsOldResult() {
        val state = ImportDialogState()
        val previous = state.beginPreview()
        state.invalidate()
        val current = state.beginPreview()
        assertFalse(state.acceptPreview(previous, ImportBundle(requests = listOf(request()))))
        assertFalse(state.canImport)
        assertTrue(state.acceptPreview(current, ImportBundle(requests = listOf(request()))))
        state.invalidate()
        assertFalse(state.canImport)
    }

    @Test
    fun cancelDiscardsInFlightPreview() {
        val state = ImportDialogState()
        val token = state.beginPreview()
        state.cancel()
        assertFalse(state.acceptPreview(token, ImportBundle(requests = listOf(request()))))
        assertFalse(state.canImport)
    }

    @Test
    fun errorsAndEmptySelectionBlockImport() {
        val state = ImportDialogState()
        var token = state.beginPreview()
        state.acceptPreview(
            token, ImportBundle(
                requests = listOf(request()), diagnostics = listOf(
                    ImportDiagnostic(ImportSeverity.ERROR, "collection", "Malformed collection")
                )
            )
        )
        assertFalse(state.canImport)
        token = state.beginPreview()
        state.acceptPreview(token, ImportBundle(requests = listOf(request(error = true))))
        assertFalse(state.canImport)
        assertFalse(state.select(0, true))
        token = state.beginPreview()
        state.acceptPreview(token, ImportBundle(requests = listOf(request())))
        assertTrue(state.select(0, false))
        assertFalse(state.canImport)
    }

    @Test
    fun consentRequiresExplicitSelectionAndFreshRenderedPreview() {
        val state = ImportDialogState()
        state.acceptPreview(state.beginPreview(), ImportBundle(requests = listOf(request(consent = true))))
        assertEquals(emptySet(), state.selected)
        assertFalse(state.canImport)
        assertTrue(state.select(0, true))
        assertFalse(state.canImport)
        val token = state.beginSelectionPreview()
        assertTrue(state.acceptSelectionPreview(token))
        assertTrue(state.canImport)
    }

    @Test
    fun standaloneEnvironmentCanBeImported() {
        val state = ImportDialogState()
        state.acceptPreview(
            state.beginPreview(), ImportBundle(
                environments = listOf(
                    ImportedEnvironment("Development", mapOf("baseUrl" to "http://localhost"))
                )
            )
        )
        assertTrue(state.canImport)
    }

    @Test
    fun failedPreviewCannotBeCommittedAndStaleFailureCannotReplaceCurrentPreview() {
        val state = ImportDialogState()
        val failed = state.beginPreview()
        assertTrue(state.fail(failed, "Preview failed"))
        assertFalse(state.canImport)
        val current = state.beginPreview()
        state.acceptPreview(current, ImportBundle(requests = listOf(request())))
        assertFalse(state.fail(failed, "Old failure"))
        assertTrue(state.canImport)
    }

    @Test
    fun commitDisablesRepeatedSubmissionAndFailureRequiresRefresh() {
        val state = ImportDialogState()
        state.acceptPreview(state.beginPreview(), ImportBundle(requests = listOf(request())))
        val token = state.beginCommit()!!
        assertFalse(state.canImport)
        assertEquals(null, state.beginCommit())
        assertTrue(state.fail(token, "Target changed"))
        assertFalse(state.canImport)
    }
}
