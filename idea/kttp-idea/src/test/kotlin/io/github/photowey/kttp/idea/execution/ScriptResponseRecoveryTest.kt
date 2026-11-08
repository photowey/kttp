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

package io.github.photowey.kttp.idea.execution

import io.github.photowey.kttp.idea.cli.KttpCliInvocation
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies recovery of script responses from execution checkpoints without attaching unrelated files.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/06
 */
class ScriptResponseRecoveryTest {
    @Test
    fun `missing or partial checkpoints cannot attach another response`() {
        val directory = Files.createTempDirectory("kttp-recovery-invalid").toFile()
        try {
            val invocation = KttpCliInvocation("/scripts/source.kttp", responseDir = directory.path)
            val failure = KttpExecutionResult("source.kttp", false, "Request cancelled")
            val checkpoint = directory.resolve("response.json")
            assertEquals(failure, recoverScriptResponse(checkpoint, invocation, failure))
            checkpoint.writeText("{\"success\":true,\"statusCode\":200")
            assertEquals(failure, recoverScriptResponse(checkpoint, invocation, failure))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `checkpoint response paths must stay within the requested response directory`() {
        val directory = Files.createTempDirectory("kttp-recovery-paths").toFile()
        try {
            val responseDirectory = directory.resolve("responses").apply { mkdirs() }
            val unrelated = directory.resolve("unrelated.json").apply { writeText("not this request") }
            val checkpoint = directory.resolve("response.json").apply {
                writeText(snapshot("source.kttp", unrelated.path))
            }
            val invocation = KttpCliInvocation("/scripts/source.kttp", responseDir = responseDirectory.path)
            val failure = KttpExecutionResult("source.kttp", false, "Request cancelled")
            assertEquals(failure, recoverScriptResponse(checkpoint, invocation, failure))

            val response = responseDirectory.resolve("actual.json").apply { writeText("{}") }
            checkpoint.writeText(snapshot("other.kttp", response.path))
            assertEquals(failure, recoverScriptResponse(checkpoint, invocation, failure))
            checkpoint.writeText(snapshot("source.kttp", response.path))
            val recovered = recoverScriptResponse(checkpoint, invocation, failure)
            assertFalse(recovered.success)
            assertFalse(recovered.assertionsPassed)
            assertEquals("Request cancelled", recovered.message)
            assertEquals(200, recovered.statusCode)
            assertEquals(response.canonicalPath, recovered.responseFilePath)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `only confirmed empty snapshots may create unique response files`() {
        val directory = Files.createTempDirectory("kttp-recovery-empty").toFile()
        try {
            val responseDirectory = directory.resolve("responses")
            val invocation = KttpCliInvocation("/scripts/source.kttp", responseDir = responseDirectory.path)
            val checkpoint = directory.resolve("response.json")
            val failure = KttpExecutionResult("source.kttp", false, "Request cancelled")
            listOf(
                snapshot("source.kttp"),
                snapshot("source.kttp", size = 42, body = "preview"),
                snapshot("source.kttp", size = 0, truncated = true),
                snapshot("source.kttp", size = 0, body = "contradictory")
            ).forEach { snapshot ->
                checkpoint.writeText(snapshot)
                val recovered = recoverScriptResponse(checkpoint, invocation, failure)
                assertEquals(200, recovered.statusCode)
                assertNull(recovered.responseFilePath)
                assertTrue(responseDirectory.listFiles().orEmpty().isEmpty())
            }

            checkpoint.writeText(snapshot("source.kttp", size = 0, body = ""))
            val first = recoverScriptResponse(checkpoint, invocation, failure)
            val second = recoverScriptResponse(checkpoint, invocation, failure)
            assertNotNull(first.responseFilePath)
            assertNotNull(second.responseFilePath)
            assertNotEquals(first.responseFilePath, second.responseFilePath)
            assertFalse(first.success)
            assertFalse(first.assertionsPassed)
            assertEquals("Request cancelled", first.message)
            assertTrue(java.io.File(first.responseFilePath!!).isFile)
            assertTrue(java.io.File(second.responseFilePath!!).isFile)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun snapshot(
        scriptName: String, responsePath: String? = null,
        size: Long? = null, body: String? = null, truncated: Boolean = false
    ) = buildJsonObject {
        put("success", true)
        put("scriptName", scriptName)
        put("message", "OK")
        put("statusCode", 200)
        responsePath?.let { put("responseFilePath", it) }
        size?.let { put("responseSize", it) }
        body?.let { put("body", it) }
        put("bodyTruncated", truncated)
    }.toString()
}
