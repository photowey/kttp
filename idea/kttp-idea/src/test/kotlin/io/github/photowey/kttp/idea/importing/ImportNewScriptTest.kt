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

package io.github.photowey.kttp.idea.importing

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import kotlinx.serialization.json.*

/**
 * Checks grouped new-script imports, stable environments and preservation of existing destination files.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/14
 */
class ImportNewScriptTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()

    fun testNewCollectionScriptSharesBindingsAndNeverOverwritesPriorImport() {
        val existing = myFixture.addFileToProject(".kttp/imports/demo.kttp.kts", "// Keep me\n")
        myFixture.addFileToProject(".kttp/imports/kttp.env.json", """{"postman_dev":{"token":"old"}}""")
        val original = myFixture.configureByText("original.kttp.kts", "// Current script unchanged\n")
        val bundle = ImportBundle(
            requests = listOf("One", "Two").map {
                ImportedRequest(
                    it,
                    "GET",
                    ImportValue("{{baseUrl}}/echo", true),
                    sourcePath = "Demo/Folder/$it"
                )
            },
            environments = listOf(ImportedEnvironment("dev", mapOf("baseUrl" to "https://example.test"))),
            sourceName = "Demo", sourceFormat = ImportFormat.POSTMAN
        )
        val result = RequestImportCoordinator(project).commit(
            bundle, setOf(0, 1),
            ImportDestination.NewScript(existing.virtualFile.parent.path, "demo.kttp.kts"), null
        )
        assertEquals("demo-2.kttp.kts", result.files.single().name)
        assertEquals("// Keep me\n", existing.text)
        assertEquals("// Current script unchanged\n", original.text)
        assertEquals(listOf("postman_dev-2"), result.environmentProfiles)
        val source = FileDocumentManager.getInstance().getDocument(result.files.single())!!.text
        assertEquals(1, Regex("val `baseUrl`: String by env").findAll(source).count())
        assertTrue(source, source.indexOf("Demo/Folder/One") < source.indexOf("Demo/Folder/Two"))
        assertTrue(result.drafts.isEmpty())
        val env = Json.parseToJsonElement(
            FileDocumentManager.getInstance().getDocument(
                existing.virtualFile.parent.findChild("kttp.env.json")!!
            )!!.text
        ).jsonObject
        assertEquals("old", env.getValue("postman_dev").jsonObject.getValue("token").jsonPrimitive.content)
    }

    fun testEnvironmentOnlyDoesNotCreateAnEmptyNewScript() {
        val directory = myFixture.tempDirFixture.findOrCreateDir(".kttp/imports")
        val result = RequestImportCoordinator(project).commit(
            ImportBundle(
                environments = listOf(ImportedEnvironment("dev", mapOf("x" to "one"))),
                sourceFormat = ImportFormat.POSTMAN
            ),
            emptySet(), ImportDestination.NewScript(directory.path, "empty.kttp.kts"), null
        )
        assertTrue(result.files.isEmpty())
        assertNull(directory.findChild("empty.kttp.kts"))
        assertNotNull(directory.findChild("kttp.env.json"))
    }
}
