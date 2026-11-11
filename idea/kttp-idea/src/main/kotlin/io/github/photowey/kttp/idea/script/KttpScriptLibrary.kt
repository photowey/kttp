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

package io.github.photowey.kttp.idea.script

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtil
import java.io.File

/**
 * Repairs the project library that exposes installed Kttp JARs in External Libraries.
 * Editor resolution receives the same JARs through the script compilation configuration.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpScriptLibrary {

    private val log = Logger.getInstance(KttpScriptLibrary::class.java)

    /** Schedules archive-root repair without writing the project model inside Kotlin's read action. */
    fun ensureInstalledLibrary(project: Project) {
        val library = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
            .getLibraryByName(KttpScriptLibraries.LIBRARY_NAME)
        val jars = KttpScriptRunUtils.getKttpRunnerJars()
        val roots = jars.map(VfsUtil::getUrlForLibraryRoot).distinct()
        if (roots.isEmpty()) {
            log.warn("No installed Kttp JARs found for script editor dependencies")
        } else if (!hasRoots(library, roots)) {
            scheduleLibraryUpdate(project, jars, roots)
        }
    }

    /** Refreshes JAR files off the UI thread, then defers model writes out of Kotlin's read action. */
    private fun scheduleLibraryUpdate(project: Project, jars: List<File>, roots: List<String>) {
        val application = ApplicationManager.getApplication()
        application.executeOnPooledThread {
            if (project.isDisposed) return@executeOnPooledThread
            jars.forEach { VfsUtil.findFileByIoFile(it, true) }
            application.invokeLater({
                if (project.isDisposed) return@invokeLater
                updateLibrary(project, roots)
            }, ModalityState.nonModal())
        }
    }

    /** Rechecks queued updates and replaces only class roots, retaining the existing library identity. */
    private fun updateLibrary(project: Project, roots: List<String>) {
        ApplicationManager.getApplication().runWriteAction {
            val table = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
            val existing = table.getLibraryByName(KttpScriptLibraries.LIBRARY_NAME)
            if (hasRoots(existing, roots)) return@runWriteAction
            val library = existing ?: table.createLibrary(KttpScriptLibraries.LIBRARY_NAME)
            val model = library.modifiableModel
            try {
                model.getUrls(OrderRootType.CLASSES).forEach { model.removeRoot(it, OrderRootType.CLASSES) }
                roots.forEach { model.addRoot(it, OrderRootType.CLASSES) }
                model.commit()
                log.info("Updated Kttp script library with ${roots.size} archive roots")
            } catch (error: Exception) {
                model.dispose()
                log.warn("Unable to update Kttp script dependency roots", error)
            }
        }
    }

    private fun hasRoots(library: Library?, roots: List<String>): Boolean =
        library?.getUrls(OrderRootType.CLASSES)?.toSet() == roots.toSet()
}
