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

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import io.github.photowey.kttp.idea.gui.GuiDraft

/**
 * Captures the destination separately from the source format and guards editor changes after preview.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
sealed interface ImportDestination {
    /**
     * An editor snapshot guarded against target changes.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    data class Script(val editor: Editor, val file: VirtualFile, val originalText: String) : ImportDestination

    /**
     * New project drafts, with script files for requests that require Follow Script.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    data object Drafts : ImportDestination

    /**
     * A new named script beside its imported environment file; existing files are never replaced.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/14
     */
    data class NewScript(val directory: String, val fileName: String) : ImportDestination

    /**
     * A user-selected directory for an environment-only import.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    data class EnvironmentDirectory(val directory: String) : ImportDestination
}

/**
 * Successfully committed targets; notification callbacks run only after persistence succeeds.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
data class ImportCommitResult(
    val drafts: List<GuiDraft>,
    val files: List<VirtualFile>,
    val environmentProfiles: List<String>
)
