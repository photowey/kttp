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

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

/**
 * Starts import choosers at the explicit source/base location, falling back to the current project.
 * Looking up a location never reads an upload file or changes the import destination.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/14
 */
internal object ImportSourceLocations {
    fun initialSelection(project: Project, selectedPath: String?, baseDirectory: String? = null): VirtualFile? {
        val base = baseDirectory?.takeIf(String::isNotBlank) ?: project.basePath
        val paths = listOfNotNull(selectedPath?.takeIf(String::isNotBlank), base, project.basePath)
        return paths.firstNotNullOfOrNull { candidate ->
            runCatching {
                val path = Path.of(candidate)
                val absolute = if (path.isAbsolute) path else Path.of(base ?: return@runCatching null).resolve(path)
                LocalFileSystem.getInstance().findFileByNioFile(absolute.normalize())
            }.getOrNull()
        }
    }
}
