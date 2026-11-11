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
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.testFramework.replaceService
import io.github.photowey.kttp.script.Kttp
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/**
 * Verifies that script dependencies expose JAR contents and repair persisted library roots.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/07
 */
class KttpScriptDependenciesTest : BasePlatformTestCase() {
    private val provider = KttpScriptLibrary()
    private lateinit var dslJar: File

    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()

    override fun setUp() {
        super.setUp()
        dslJar = File(myFixture.tempDirPath, "kttp-dsl.jar")
        JarOutputStream(dslJar.outputStream()).use { output ->
            val entry = "io/github/photowey/kttp/script/Kttp.class"
            output.putNextEntry(JarEntry(entry))
            Kttp::class.java.getResourceAsStream("/$entry")!!.use { it.copyTo(output) }
            output.closeEntry()
        }
        ApplicationManager.getApplication().replaceService(
            KttpArtifactsProvider::class.java,
            object : KttpArtifactsProvider {
                override fun getRunnerJars() = listOf(dslJar)
            }, testRootDisposable
        )
    }

    override fun tearDown() {
        try {
            ApplicationManager.getApplication().runWriteAction {
                library()?.let { LibraryTablesRegistrar.getInstance().getLibraryTable(project).removeLibrary(it) }
            }
        } finally {
            super.tearDown()
        }
    }

    fun testNewLibraryExposesTheScriptTemplateInsideTheArchive() {
        provider.ensureInstalledLibrary(project)
        await("Kttp library created") { library()?.getUrls(OrderRootType.CLASSES)?.isNotEmpty() == true }
        assertEquals(listOf(VfsUtil.getUrlForLibraryRoot(dslJar)), library()!!.getUrls(OrderRootType.CLASSES).toList())
        val roots = library()!!.getFiles(OrderRootType.CLASSES)
        assertEquals(1, roots.size)
        assertNotNull("A CLASSES root must expose archive entries, not the JAR file itself",
            roots.single().findFileByRelativePath("io/github/photowey/kttp/script/Kttp.class"))
    }

    fun testLegacyFileRootsAreRepairedWithoutDeletingTheLibrary() {
        val jarFile = VfsUtil.findFileByIoFile(dslJar, true)!!
        val existing = createLibrary(jarFile.url)
        provider.ensureInstalledLibrary(project)
        await("Legacy roots converted to archive roots") {
            library()?.getUrls(OrderRootType.CLASSES)?.toList() == listOf(VfsUtil.getUrlForLibraryRoot(dslJar))
        }
        assertSame(existing, library())
    }

    fun testStaleVersionRootsAreReplacedWithTheInstalledClasspath() {
        createLibrary("jar:///missing/previous-kttp-version.jar!/")
        provider.ensureInstalledLibrary(project)
        await("Installed JAR replaces obsolete roots") {
            library()?.getUrls(OrderRootType.CLASSES)?.toList() == listOf(VfsUtil.getUrlForLibraryRoot(dslJar))
        }
    }

    private fun createLibrary(root: String): Library {
        var result: Library? = null
        ApplicationManager.getApplication().runWriteAction {
            val created = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
                .createLibrary(KttpScriptLibraries.LIBRARY_NAME)
            val model = created.modifiableModel
            model.addRoot(root, OrderRootType.CLASSES)
            model.commit()
            result = created
        }
        return result!!
    }

    private fun library(): Library? = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
        .getLibraryByName(KttpScriptLibraries.LIBRARY_NAME)

    private fun await(description: String, condition: () -> Boolean) =
        PlatformTestUtil.waitWithEventsDispatching(description, condition, 10)
}
