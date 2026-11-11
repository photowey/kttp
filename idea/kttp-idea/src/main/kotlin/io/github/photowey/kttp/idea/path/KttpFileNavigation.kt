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

package io.github.photowey.kttp.idea.path

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import io.github.photowey.kttp.idea.KttpUiActions
import java.io.File

/**
 * Resolves local paths in the background and opens files on the platform's write-safe UI queue.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/11
 */
internal object KttpFileNavigation {
    fun open(project: Project, file: VirtualFile) {
        KttpUiActions.submit(project) {
            if (file.isValid) OpenFileDescriptor(project, file).navigate(true)
        }
    }

    fun open(project: Project, path: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            if (project.isDisposed || !File(path).isFile) return@executeOnPooledThread
            val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path) ?: return@executeOnPooledThread
            open(project, file)
        }
    }
}
