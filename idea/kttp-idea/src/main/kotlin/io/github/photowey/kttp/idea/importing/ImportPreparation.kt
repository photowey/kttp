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

/**
 * Checks host conversion boundaries per request before the dialog selects or renders a batch.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
internal object ImportPreparation {
    fun review(bundle: ImportBundle, baseDirectory: String?, existingScript: String = ""): ImportBundle = bundle.copy(
        requests = bundle.requests.map { request ->
            if (!request.selectable) request else try {
                require(request.method !in setOf("GET", "HEAD") || request.body == ImportBody.None) {
                    "Kttp cannot send a body with GET or HEAD; remove it before importing"
                }
                ImportScriptRenderer.render(request, existingScript, baseDirectory)
                request
            } catch (failure: IllegalArgumentException) {
                request.copy(
                    diagnostics = request.diagnostics + ImportDiagnostic(
                        ImportSeverity.ERROR, request.sourcePath,
                        failure.message ?: "This request requires manual conversion before importing"
                    )
                )
            }
        }
    )
}
