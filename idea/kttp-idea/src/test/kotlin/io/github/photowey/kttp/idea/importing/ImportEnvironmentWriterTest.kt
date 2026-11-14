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

import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

/**
 * Verifies optional environments, override precedence and preservation of existing profiles.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
class ImportEnvironmentWriterTest {
    @Test
    fun optionalEnvironmentNeedsNoFileForLiteralRequests() {
        assertEquals(null to emptyList<String>(), ImportEnvironmentWriter.merge(null, ImportBundle()))
    }

    @Test
    fun sameNameCreatesNewProfileAndMergesCollectionDefaults() {
        val old = """{"dev":{"secret":"existing"}}"""
        val bundle = ImportBundle(
            variables = mapOf("host" to "collection", "fallback" to "yes"),
            environments = listOf(ImportedEnvironment("dev", mapOf("host" to "environment")))
        )
        val (text, names) = ImportEnvironmentWriter.merge(old, bundle)
        assertEquals(listOf("dev-2"), names)
        val root = Json.parseToJsonElement(assertNotNull(text)).jsonObject
        assertEquals("existing", root.getValue("dev").jsonObject.getValue("secret").jsonPrimitive.content)
        assertEquals("environment", root.getValue("dev-2").jsonObject.getValue("host").jsonPrimitive.content)
        assertEquals("yes", root.getValue("dev-2").jsonObject.getValue("fallback").jsonPrimitive.content)
    }

    @Test
    fun collectionDefaultsCreateProfileWithoutEnvironmentInput() {
        val (text, names) = ImportEnvironmentWriter.merge(null, ImportBundle(variables = mapOf("host" to "local")))
        assertEquals(listOf("Imported"), names)
        assertTrue(assertNotNull(text).contains("local"))
    }

    @Test
    fun nestedVariablesResolveAndUnresolvedValuesAreNotSentLiterally() {
        val bundle = ImportBundle(
            variables = mapOf("host" to "example.com"), environments = listOf(
                ImportedEnvironment(
                    "dev",
                    mapOf("url" to "https://{{host}}", "bad" to "{{missing}}", "cycle" to "{{cycle}}")
                )
            )
        )
        val text = assertNotNull(ImportEnvironmentWriter.merge(null, bundle).first)
        val profile = Json.parseToJsonElement(text).jsonObject.getValue("dev").jsonObject
        assertEquals("https://example.com", profile.getValue("url").jsonPrimitive.content)
        assertFalse("bad" in profile)
        assertFalse("cycle" in profile)
    }

    @Test
    fun malformedExistingFileMustNotBeReplaced() {
        assertFailsWith<IllegalArgumentException> {
            ImportEnvironmentWriter.merge("broken", ImportBundle(variables = mapOf("x" to "value")))
        }
    }

    @Test
    fun postmanNamesKeepTheirOriginAndNeverRenameExistingProfiles() {
        val bundle = ImportBundle(
            variables = mapOf("host" to "example.test"),
            sourceName = "Demo",
            sourceFormat = ImportFormat.POSTMAN
        )
        val (text, names) = ImportEnvironmentWriter.merge("""{"postman_Demo":{"host":"keep"}}""", bundle)
        assertEquals(listOf("postman_Demo-2"), names)
        val root = Json.parseToJsonElement(assertNotNull(text)).jsonObject
        assertEquals("keep", root.getValue("postman_Demo").jsonObject.getValue("host").jsonPrimitive.content)
        assertEquals("example.test", root.getValue("postman_Demo-2").jsonObject.getValue("host").jsonPrimitive.content)
        assertTrue(
            ImportEnvironmentWriter.profileNames(CurlImportParser().parse(ImportInput("curl https://example.test")))
                .isEmpty()
        )
    }

    @Test
    fun oversizedEnvironmentValuesFailBeforeCommitInsteadOfGrowingWithoutLimit() {
        assertFailsWith<IllegalArgumentException> {
            ImportEnvironmentWriter.merge(null, ImportBundle(variables = mapOf("large" to "x".repeat(1_048_577))))
        }
    }

}
