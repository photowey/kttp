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
import io.github.photowey.kttp.idea.env.KttpEnvironmentService
import io.github.photowey.kttp.idea.importing.*

/**
 * Exercises activation defaults, explicit choices and collision-renamed committed profiles.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/14
 */
class ImportEnvironmentSelectionTest : BasePlatformTestCase() {
    private val bundle =
        ImportBundle(environments = listOf(ImportedEnvironment("dev", emptyMap())), sourceFormat = ImportFormat.POSTMAN)

    private fun result(vararg names: String) = ImportCommitResult(emptyList(), emptyList(), names.toList())

    override fun tearDown() {
        try {
            KttpEnvironmentService.getInstance(project).setSelectedEnvironment(null)
        } finally {
            super.tearDown()
        }
    }

    fun testSoleProfileDefaultsToActualRenamedResult() {
        val controls = ImportEnvironmentSelection(project)
        controls.configure(bundle)
        assertTrue(controls.useImported.isSelected)
        controls.apply(result("postman_dev-2"))
        assertEquals("postman_dev-2", KttpEnvironmentService.getInstance(project).selectedEnvironment)
    }

    fun testExistingChoiceIsPreservedUnlessUserOptsIn() {
        val service = KttpEnvironmentService.getInstance(project)
        service.setSelectedEnvironment("existing")
        val controls = ImportEnvironmentSelection(project)
        controls.configure(bundle)
        controls.apply(result("postman_dev"))
        assertEquals("existing", service.selectedEnvironment)
        controls.useImported.doClick()
        controls.apply(result("postman_dev"))
        assertEquals("postman_dev", service.selectedEnvironment)
    }

    fun testOptOutAndCancellationLeaveEnvironmentUnchanged() {
        val controls = ImportEnvironmentSelection(project)
        controls.configure(bundle)
        controls.useImported.doClick()
        controls.apply(result("postman_dev"))
        assertNull(KttpEnvironmentService.getInstance(project).selectedEnvironment)
        controls.configure(bundle)
        assertNull(KttpEnvironmentService.getInstance(project).selectedEnvironment)
    }

    fun testMultipleProfilesRequireAnExplicitProfileChoice() {
        val controls = ImportEnvironmentSelection(project)
        controls.configure(
            bundle.copy(
                environments = listOf(
                    ImportedEnvironment("dev", emptyMap()),
                    ImportedEnvironment("qa", emptyMap())
                )
            )
        )
        controls.useImported.doClick()
        controls.apply(result("postman_dev", "postman_qa-2"))
        assertNull(KttpEnvironmentService.getInstance(project).selectedEnvironment)
        controls.profiles.selectedIndex = 2
        controls.apply(result("postman_dev", "postman_qa-2"))
        assertEquals("postman_qa-2", KttpEnvironmentService.getInstance(project).selectedEnvironment)
    }

    fun testEnvironmentChangedWhileImportingIsNotOverriddenByAutomaticDefault() {
        val controls = ImportEnvironmentSelection(project)
        controls.configure(bundle)
        val service = KttpEnvironmentService.getInstance(project)
        service.setSelectedEnvironment("changed")
        controls.apply(result("postman_dev"))
        assertEquals("changed", service.selectedEnvironment)
    }
}
