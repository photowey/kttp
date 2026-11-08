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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent

/**
 * VFS delivery may be off the EDT. Collect changes here, apply them only on the UI owner.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal class SidebarVfsListener(
    private val project: Project,
    private val discovery: SidebarFileDiscovery,
    private val invalidate: (String) -> Unit,
    private val changed: (VirtualFile) -> Unit,
    private val refreshScripts: () -> Unit,
    private val refreshResponses: () -> Unit
) : BulkFileListener {
    fun install() {
        project.messageBus.connect(project).subscribe(VirtualFileManager.VFS_CHANGES, this)
    }

    override fun after(events: List<VFileEvent>) {
        val changes = Changes()
        events.forEach { collect(it, changes) }
        if (changes.isEmpty()) return
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                changes.invalidated.forEach(invalidate)
                changes.changed.filter { it.isValid }.forEach(changed)
                if (changes.scripts) refreshScripts()
                if (changes.responses) refreshResponses()
            }
        }
    }

    private fun collect(event: VFileEvent, changes: Changes) {
        val file = event.file ?: return
        when (event) {
            is VFileContentChangeEvent -> {
                if (file.name.endsWith(".kttp.kts")) {
                    changes.invalidated += event.path
                    changes.changed += file
                }
                changes.responses = changes.responses || discovery.isResponsePath(event.path)
            }

            is VFileCreateEvent -> pathsChanged(changes, event.path)
            is VFileDeleteEvent -> pathsChanged(changes, event.path)
            is VFileMoveEvent -> pathsChanged(changes, "${event.oldParent?.path.orEmpty()}/${file.name}", file.path)
            is VFilePropertyChangeEvent -> if (event.propertyName == VirtualFile.PROP_NAME) {
                pathsChanged(changes, "${file.parent?.path}/${event.oldValue}", file.path)
            }
        }
    }

    private fun pathsChanged(changes: Changes, vararg paths: String) {
        val scriptPaths = paths.filter { discovery.isRequestPath(it) || it.endsWith(".kttp.kts") }
        changes.invalidated += scriptPaths
        changes.scripts = changes.scripts || scriptPaths.isNotEmpty()
        changes.responses = changes.responses || paths.any(discovery::isResponsePath)
    }

    /**
     * Aggregates invalidated paths and script or response changes from VFS events.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    private class Changes {
        val invalidated = linkedSetOf<String>()
        val changed = linkedSetOf<VirtualFile>()
        var scripts = false
        var responses = false
        fun isEmpty() = invalidated.isEmpty() && changed.isEmpty() && !scripts && !responses
    }
}
