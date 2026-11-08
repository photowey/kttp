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

package io.github.photowey.kttp.idea.toolwindow

/**
 * Associates a response value with its display name and modification time.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal data class SidebarResponseEntry<T>(
    val value: T,
    val name: String,
    val modifiedAt: Long
)

/**
 * Chooses sidebar selection targets and orders visible response entries.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal object SidebarPresentation {

    fun preferredSelectionKey(currentSelectionKey: String?, pendingSelectionKey: String?): String? =
        pendingSelectionKey ?: currentSelectionKey

    fun <T> visibleResponses(entries: Iterable<SidebarResponseEntry<T>>): List<SidebarResponseEntry<T>> =
        entries
            .filterNot { it.name == "kttp-history.json" }
            .sortedWith(compareByDescending<SidebarResponseEntry<T>> { it.modifiedAt }.thenBy { it.name })
}
