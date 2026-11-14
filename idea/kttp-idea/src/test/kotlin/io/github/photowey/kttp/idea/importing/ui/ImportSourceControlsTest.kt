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

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import io.github.photowey.kttp.idea.importing.ImportFormat
import java.awt.Container
import javax.swing.JTextField

/**
 * Verifies import source controls start in the active project directory.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/14
 */
class ImportSourceControlsTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()

    fun testBaseDirectoryDefaultsToProjectBasePath() {
        val controls = ImportSourceControls(project, ImportFormat.POSTMAN)
        assertEquals(project.basePath, controls.snapshot().baseDirectory)
        assertEquals(project.basePath, controls.snapshot().read().baseDirectory)
    }

    fun testBrowseStartsAtProjectWhenNoPathWasEntered() {
        val file = myFixture.addFileToProject("sample.json", "{}").virtualFile
        assertEquals(project.basePath, ImportSourceLocations.initialSelection(project, "")?.path)
    }

    fun testBrowseHonorsSelectedSourceAndExplicitBaseDirectory() {
        val file = myFixture.addFileToProject("uploads/sample.json", "{}").virtualFile
        assertEquals(file, ImportSourceLocations.initialSelection(project, file.path))
        assertEquals(file.parent, ImportSourceLocations.initialSelection(project, "", file.parent.path))
        assertEquals(file, ImportSourceLocations.initialSelection(project, "sample.json", file.parent.path))
    }

    fun testInvalidOrMissingSourceFallsBackToProject() {
        val file = myFixture.addFileToProject("sample.json", "{}").virtualFile
        assertEquals(project.basePath, ImportSourceLocations.initialSelection(project, "\u0000invalid")?.path)
        assertEquals(project.basePath, ImportSourceLocations.initialSelection(project, "missing.json")?.path)
    }

    fun testClearedBaseDirectoryRetainsSourceFileDirectoryFallback() {
        val file = myFixture.addFileToProject("fixtures/collection.json", "{}").virtualFile
        val controls = ImportSourceControls(project, ImportFormat.POSTMAN)
        allTextFields(controls).single { it.accessibleContext.accessibleName == "Base directory" }.text = ""
        allTextFields(controls).single {
            it.accessibleContext.accessibleName == "Collection (optional for environment-only import) file"
        }.text = file.path
        assertEquals(file.parent.path, controls.snapshot().read().baseDirectory)
    }

    private fun allTextFields(container: Container): List<JTextField> = buildList {
        container.components.forEach { component ->
            if (component is JTextField) add(component)
            if (component is Container) addAll(allTextFields(component))
        }
    }
}
