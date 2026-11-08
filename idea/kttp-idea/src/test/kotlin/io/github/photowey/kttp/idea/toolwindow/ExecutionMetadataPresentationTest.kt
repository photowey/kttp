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

import com.intellij.testFramework.LightPlatformTestCase
import io.github.photowey.kttp.idea.execution.KttpExecutionResult
import io.github.photowey.kttp.idea.execution.RequestExecutionMode
import io.github.photowey.kttp.idea.io.HistoryPersistence
import io.github.photowey.kttp.idea.io.HistoryRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Component
import java.awt.Container
import java.io.File
import java.nio.file.Files
import javax.swing.JLabel

/**
 * Verifies execution mode, timing, and failure metadata across history and response presentation.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class ExecutionMetadataPresentationTest : LightPlatformTestCase() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun testHistoryRoundTripRetainsActualModeAndSeparateTimes() {
        val directory = Files.createTempDirectory("kttp-history-execution").toFile()
        val state = File(directory, "history.json")
        try {
            val record = json.decodeFromString(
                HistoryPersistence.Record.serializer(),
                """{"scriptName":"selected","responseFilePath":"/responses/one.json","executionMode":"FOLLOW_SCRIPT","totalTimeMs":560,"httpTimeMs":7}"""
            )
            HistoryRepository { state }.append(listOf(record))
            val reloaded = HistoryRepository { state }.load().single()
            val metadata = Json.parseToJsonElement(
                json.encodeToString(
                    HistoryPersistence.Record.serializer(),
                    reloaded
                )
            ).jsonObject
            assertEquals("FOLLOW_SCRIPT", metadata["executionMode"]?.jsonPrimitive?.content)
            assertEquals("560", metadata["totalTimeMs"]?.jsonPrimitive?.content)
            assertEquals("7", metadata["httpTimeMs"]?.jsonPrimitive?.content)
        } finally {
            directory.deleteRecursively()
        }
    }

    fun testOldHistoryCanStillLoadWithoutInventedTimings() {
        val directory = Files.createTempDirectory("kttp-history-legacy").toFile()
        val state = File(directory, "history.json")
        state.writeText("""{"entries":[{"scriptName":"old","responseFilePath":"/old.json"}]}""")
        try {
            val record = HistoryRepository { state }.load().single()
            assertEquals("old", record.scriptName)
            val metadata =
                Json.parseToJsonElement(json.encodeToString(HistoryPersistence.Record.serializer(), record)).jsonObject
            assertTrue(metadata["totalTimeMs"] == null || metadata["totalTimeMs"].toString() == "null")
        } finally {
            directory.deleteRecursively()
        }
    }

    fun testThenFailureKeepsReceivedResponseAndHistoryWithActualExecutionMode() {
        val previous = HistoryPersistence.load()
        val responseFile = Files.createTempFile("kttp-then-response", ".json").toFile().apply { writeText("{}") }
        try {
            HistoryPersistence.clear()
            val response = ResponseTabPanel(project)
            val history = HistoryTabPanel(project)
            val main = MainContentPanel(project, response, history, AssertionsTabPanel(project))
            val result = KttpExecutionResult(
                "source request", false, "then-broken", responseFile.path,
                statusCode = 200, body = "{}", assertionsPassed = false,
                executionMode = RequestExecutionMode.FOLLOW_SCRIPT, totalTimeMs = 560, httpTimeMs = 7
            )
            MainContentPanel::class.java.getDeclaredMethod(
                "showExecutionResult",
                KttpExecutionResult::class.java,
                String::class.java
            )
                .apply { isAccessible = true }.invoke(main, result, "/scripts/source.kttp.kts")
            val labels = descendants(response).filterIsInstance<JLabel>().map { it.text.orEmpty() }
            assertTrue(
                "HTTP response must stay visible after then failed: $labels",
                labels.any { it.contains("HTTP 200") })
            assertTrue(
                "Response must show the completed mode rather than the current editor mode",
                labels.any { it.contains("Follow Script") })
            assertTrue(labels.any { it.contains("Total 560 ms") })
            assertTrue(labels.any { it.contains("HTTP 7 ms") })
            assertTrue(labels.any { it.contains("then-broken") })
            val stored = HistoryPersistence.load().single()
            assertEquals(responseFile.path, stored.responseFilePath)
            val metadata =
                Json.parseToJsonElement(json.encodeToString(HistoryPersistence.Record.serializer(), stored)).jsonObject
            assertEquals("FOLLOW_SCRIPT", metadata["executionMode"]?.jsonPrimitive?.content)
        } finally {
            HistoryPersistence.clear()
            HistoryPersistence.append(previous)
            responseFile.delete()
        }
    }

    private fun descendants(root: Component): List<Component> = listOf(root) +
        if (root is Container) root.components.flatMap(::descendants) else emptyList()
}
