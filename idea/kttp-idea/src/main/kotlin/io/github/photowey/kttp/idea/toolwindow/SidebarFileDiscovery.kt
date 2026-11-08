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

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import io.github.photowey.kttp.idea.path.KttpConsolesPath
import io.github.photowey.kttp.idea.path.KttpProjectPath
import java.io.File

/**
 * Separates discovered script files into project and console groups.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal data class SidebarFileGroups(val project: List<VirtualFile>, val kttp: List<VirtualFile>)

/**
 * Disk/VFS discovery and directory membership; does not own UI or parse caches.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal class SidebarFileDiscovery(private val project: Project) {
    fun scripts(): SidebarFileGroups {
        val projectFiles = ReadAction.compute<List<VirtualFile>, RuntimeException> {
            if (project.isDisposed) emptyList()
            else ProjectRootManager.getInstance(project).contentRoots.flatMap(::scriptsUnder)
        }
        return SidebarFileGroups(
            projectFiles.distinct(),
            findDirectory(KttpConsolesPath.getKttpConsolesDir())?.let(::scriptsUnder).orEmpty()
        )
    }

    fun responses(): SidebarFileGroups = SidebarFileGroups(
        project.basePath?.let { listResponses(KttpProjectPath.getResponsesDir(it)) }.orEmpty(),
        listResponses(KttpConsolesPath.getResponsesDir())
    )

    fun isRequestPath(path: String): Boolean =
        project.basePath?.let { contains(KttpProjectPath.getRequestsDir(it), path) } == true ||
            contains(KttpConsolesPath.getKttpConsolesDir(), path)

    fun isResponsePath(path: String): Boolean =
        project.basePath?.let { contains(KttpProjectPath.getResponsesDir(it), path) } == true ||
            contains(KttpConsolesPath.getResponsesDir(), path)

    private fun scriptsUnder(directory: VirtualFile): List<VirtualFile> {
        if (!directory.isDirectory || !directory.isValid || project.isDisposed) return emptyList()
        return directory.children.flatMap { child ->
            when {
                child.isDirectory -> scriptsUnder(child)
                child.name.endsWith(".kttp.kts") -> listOf(child)
                else -> emptyList()
            }
        }
    }

    private fun listResponses(directory: File): List<VirtualFile> {
        val file = findDirectory(directory) ?: return emptyList()
        // Response writers use disk I/O. Finding an existing VFS directory does not refresh its cached children.
        // This discovery runs in the background; refresh only the flat response directory before listing it.
        VfsUtil.markDirtyAndRefresh(false, false, false, file)
        return SidebarPresentation.visibleResponses(file.children.filterNot { it.isDirectory }.map {
            SidebarResponseEntry(it, it.name, it.timeStamp)
        }).map { it.value }
    }

    private fun findDirectory(directory: File): VirtualFile? =
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(directory)

    private fun contains(directory: File, path: String): Boolean {
        val root = directory.absolutePath.replace('\\', '/').trimEnd('/')
        val normalized = path.replace('\\', '/')
        return normalized == root || normalized.startsWith("$root/")
    }
}
