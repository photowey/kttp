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

package io.github.photowey.kttp.idea.gui

import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import io.github.photowey.kttp.idea.execution.RequestExecutionMode
import io.github.photowey.kttp.idea.toolwindow.EditorWorkingSession
import io.github.photowey.kttp.idea.path.KttpConsolesPath
import io.github.photowey.kttp.idea.path.KttpProjectPath
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Verifies saved execution choices and their independence from draft content and workspace snapshots.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class RequestExecutionPreferencesTest : LightPlatformTestCase() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun testExplicitScriptModeSurvivesSaveAndServiceReload() = withArchive {
        val draft = json.decodeFromString(
            GuiDraft.serializer(),
            """{"id":"mode-test","title":"Mode","ownership":"GUI_OWNED","request":{},"executionMode":"FOLLOW_SCRIPT"}"""
        )
        GuiDraftService(project).saveDraft(draft)
        val reloaded = GuiDraftService(project).findDraft("mode-test")!!
        val savedMode = Json.parseToJsonElement(json.encodeToString(GuiDraft.serializer(), reloaded))
            .jsonObject["executionMode"]?.jsonPrimitive?.content
        assertEquals("Persisted choice must survive a new service", "FOLLOW_SCRIPT", savedMode)
    }

    fun testLegacyDraftDefaultsToQuickRequest() {
        val legacy = json.decodeFromString(
            GuiDraft.serializer(),
            """{"id":"legacy","title":"Legacy","ownership":"GUI_OWNED","request":{}}"""
        )
        val savedMode = Json.parseToJsonElement(json.encodeToString(GuiDraft.serializer(), legacy))
            .jsonObject["executionMode"]?.jsonPrimitive?.content
        assertEquals("QUICK_REQUEST", savedMode)
    }

    fun testRequestChoicesSurviveWorkspaceSerializationIndependently() {
        val preferences = RequestExecutionPreferences()
        preferences.remember("import:/requests.kttp.kts:0", RequestExecutionMode.QUICK_REQUEST)
        preferences.remember("import:/requests.kttp.kts:1", RequestExecutionMode.FOLLOW_SCRIPT)
        val persisted = XmlSerializer.serialize(preferences.state)
        val reloaded = RequestExecutionPreferences().apply {
            loadState(XmlSerializer.deserialize(persisted, RequestExecutionPreferences.Settings::class.java))
        }
        assertEquals(
            RequestExecutionMode.QUICK_REQUEST,
            reloaded.modeFor("import:/requests.kttp.kts:0", RequestExecutionMode.FOLLOW_SCRIPT)
        )
        assertEquals(
            RequestExecutionMode.FOLLOW_SCRIPT,
            reloaded.modeFor("import:/requests.kttp.kts:1", RequestExecutionMode.QUICK_REQUEST)
        )
        assertEquals(
            RequestExecutionMode.FOLLOW_SCRIPT,
            reloaded.modeFor("new-import", RequestExecutionMode.FOLLOW_SCRIPT)
        )
    }

    fun testSwitchInvalidatesCompletionWithoutDroppingDirtyRequest() {
        val original = GuiDraftService(project).createEmptyDraft()
        val session = EditorWorkingSession(original)
        val changed =
            original.copy(request = original.request.copy(target = RequestTarget(ValueRef.Literal("https://changed.example/"))))
        session.edit(changed)
        val sent = session.draft
        val revision = session.revision
        session.selectExecutionMode(RequestExecutionMode.FOLLOW_SCRIPT)
        assertFalse(session.stillMatches(sent, revision))
        assertEquals(changed.request, session.draft.request)
        assertTrue(session.draft.meta.dirty)
        session.loadPersisted(original)
        assertEquals(changed.request, session.draft.request)
        assertEquals(RequestExecutionMode.FOLLOW_SCRIPT, session.draft.executionMode)
    }

    fun testWorkspaceSnapshotIsSafeWhileExecutionChoicesChange() {
        val preferences = RequestExecutionPreferences()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        val start = java.util.concurrent.CountDownLatch(1)
        try {
            val changing = pool.submit {
                start.await()
                repeat(1200) { index ->
                    preferences.remember("request-$index", RequestExecutionMode.FOLLOW_SCRIPT)
                    preferences.state
                }
            }
            val saving = pool.submit {
                start.await()
                repeat(1200) { preferences.state.modes.toMap() }
            }
            start.countDown()
            changing.get(10, java.util.concurrent.TimeUnit.SECONDS)
            saving.get(10, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(1200, preferences.state.modes.size)
        } finally {
            pool.shutdownNow()
        }
    }

    private fun withArchive(action: () -> Unit) {
        val directory = project.basePath?.let(KttpProjectPath::getGuiStateDir) ?: KttpConsolesPath.getGuiStateDir()
        val file = File(directory, "drafts.json")
        val previous = file.takeIf(File::isFile)?.readBytes()
        file.delete()
        try {
            action()
        } finally {
            file.delete()
            if (previous != null) file.writeBytes(previous)
        }
    }
}
