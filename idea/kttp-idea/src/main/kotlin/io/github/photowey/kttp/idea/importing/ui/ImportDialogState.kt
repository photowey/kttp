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

package io.github.photowey.kttp.idea.importing.ui

import io.github.photowey.kttp.idea.importing.ImportBundle
import io.github.photowey.kttp.idea.importing.ImportSeverity

/**
 * Owns preview generations and selection gates independently of Swing and background workers.
 * Every input or target edit invalidates its token, so a late worker cannot enable Import.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
class ImportDialogState {
    private var generation = 0L
    private var closed = false
    private var ready = false
    private var committing = false
    var bundle: ImportBundle? = null
        private set
    var selected: Set<Int> = emptySet()
        private set
    var error: String? = null
        private set

    val canImport: Boolean
        get() = !closed && ready && !committing && error == null && bundle?.let { value ->
            value.diagnostics.none { it.severity == ImportSeverity.ERROR } &&
                (selected.isNotEmpty() || value.environments.isNotEmpty()) &&
                selected.all { index -> value.requests.getOrNull(index)?.selectable == true }
        } == true

    fun invalidate() {
        generation++
        ready = false
        bundle = null
        selected = emptySet()
        error = null
    }

    fun beginPreview(): Long {
        invalidate()
        return generation
    }

    fun isCurrent(token: Long): Boolean = !closed && token == generation

    fun acceptPreview(token: Long, value: ImportBundle): Boolean {
        if (!isCurrent(token)) return false
        bundle = value
        selected = value.requests.indices.filter { value.requests[it].selectedByDefault }.toSet()
        ready = true
        error = null
        return true
    }

    fun select(index: Int, checked: Boolean): Boolean {
        if (closed || committing || bundle?.requests?.getOrNull(index)?.selectable != true) return false
        selected = if (checked) selected + index else selected - index
        generation++
        ready = false
        error = null
        return true
    }

    fun beginSelectionPreview(): Long {
        generation++
        ready = false
        return generation
    }

    fun acceptSelectionPreview(token: Long): Boolean {
        if (!isCurrent(token) || bundle == null) return false
        ready = true
        error = null
        return true
    }

    fun beginCommit(): Long? {
        if (!canImport) return null
        committing = true
        ready = false
        return generation
    }

    fun fail(token: Long, message: String): Boolean {
        if (!isCurrent(token)) return false
        committing = false
        ready = false
        error = message
        return true
    }

    fun cancel() {
        closed = true
        generation++
        ready = false
        bundle = null
        selected = emptySet()
    }
}
