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
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.v1.ScriptAdditionalIdeaDependenciesProvider
import java.io.File

/**
 * Supplies installed Kttp JAR roots to the Kotlin editor and repairs stale script libraries.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpScriptAdditionalIdeaDependenciesProvider : ScriptAdditionalIdeaDependenciesProvider {

    private val log = Logger.getInstance(KttpScriptAdditionalIdeaDependenciesProvider::class.java)

    /** Kttp scripts use their installed dependency library without inheriting the host module's classpath. */
    override fun getRelatedModules(file: VirtualFile, project: Project): List<Module> {
        if (isKttpFile(file)) return emptyList()
        return listOfNotNull(ModuleUtil.findModuleForFile(file, project))
    }

    override fun getRelatedLibraries(file: VirtualFile, project: Project): List<Library> {
        if (!isKttpFile(file)) return emptyList()

        val library = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
            .getLibraryByName(KttpScriptLibraries.LIBRARY_NAME)
        val jars = KttpScriptRunUtils.getKttpRunnerJars()
        val roots = jars.map(VfsUtil::getUrlForLibraryRoot).distinct()
        if (roots.isEmpty()) {
            log.warn("No installed Kttp JARs found for script editor dependencies")
        } else if (!hasRoots(library, roots)) {
            scheduleLibraryUpdate(project, jars, roots)
        }
        return listOfNotNull(library)
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

    private fun isKttpFile(file: VirtualFile): Boolean = file.name.endsWith(".kttp.kts")
}
