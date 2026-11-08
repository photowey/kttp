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

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import io.github.photowey.kttp.idea.execution.RequestExecutionMode

/**
 * Per-request UI preference; never writes to source scripts or changes a draft's HTTP data.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
@Service(Service.Level.PROJECT)
@State(name = "KttpRequestExecution", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class RequestExecutionPreferences : PersistentStateComponent<RequestExecutionPreferences.Settings> {
    /**
     * Stores per-request execution mode choices in project workspace state.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    class Settings {
        var modes: MutableMap<String, String> = linkedMapOf()
    }

    private var settings = Settings()

    @Synchronized
    override fun getState(): Settings = Settings().also { it.modes.putAll(settings.modes) }

    @Synchronized
    override fun loadState(state: Settings) {
        settings = Settings().also { it.modes.putAll(state.modes) }
    }

    @Synchronized
    fun modeFor(id: String, fallback: RequestExecutionMode): RequestExecutionMode =
        settings.modes[id]?.let { value -> RequestExecutionMode.entries.find { it.name == value } } ?: fallback

    @Synchronized
    fun remember(id: String, mode: RequestExecutionMode) {
        settings.modes[id] = mode.name
    }
}
