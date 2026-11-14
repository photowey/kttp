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
import io.github.photowey.kttp.idea.importing.ImportDestination
import io.github.photowey.kttp.idea.importing.ImportBundle
import java.nio.file.Path

/**
 * Checks entry-specific defaults, editable new-file destinations and invalidation after target edits.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/14
 */
class ImportDestinationControlsTest : BasePlatformTestCase() {
    fun testEditorAndGuiKeepTheirDefaultsAndCanChooseOneNewScript() {
        myFixture.configureByText("target.kttp.kts", "// unchanged")
        val original =
            ImportDestination.Script(myFixture.editor, myFixture.file.virtualFile, myFixture.editor.document.text)
        for (entry in listOf(original, ImportDestination.Drafts)) {
            val controls = ImportDestinationControls(project, entry)
            assertEquals(entry, controls.destination())
            controls.target.selectedIndex = 1
            assertEquals(
                Path.of(project.basePath!!, ".kttp", "imports").toString(),
                (controls.destination() as ImportDestination.NewScript).directory
            )
            controls.suggest(ImportBundle(sourceName = "Demo Collection"))
            assertEquals("demo-collection.kttp.kts", (controls.destination() as ImportDestination.NewScript).fileName)
            controls.fileName.text = "custom.kttp.kts"
            controls.suggest(ImportBundle(sourceName = "Different"))
            assertEquals("custom.kttp.kts", (controls.destination() as ImportDestination.NewScript).fileName)
        }
    }

    fun testTargetChangesInvalidatePreviewAndUnsafeNamesAreRejected() {
        val controls = ImportDestinationControls(project, ImportDestination.Drafts)
        var edits = 0
        controls.onChanged = { edits++ }
        controls.target.selectedIndex = 1
        controls.fileName.text = "../escape.kttp.kts"
        assertNotNull(controls.error())
        controls.fileName.text = "good.kttp.kts"
        controls.directory.text = "relative-dir"
        assertNotNull(controls.error())
        assertTrue(edits >= 3)
    }
}
