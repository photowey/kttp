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

package io.github.photowey.kttp.idea.run

import com.intellij.execution.configurations.LocatableRunConfigurationOptions

/**
 * Run configuration options for Kttp scripts.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpRunConfigurationOptions : LocatableRunConfigurationOptions() {

    private val myScriptPath = string("").provideDelegate(this, "scriptPath")
    private val mySelectedEnvironment = string().provideDelegate(this, "selectedEnvironment")
    private val myCoordinates = string().provideDelegate(this, "coordinates")
    private val myRunStrategySelection =
        enum(ScriptRunStrategySelection.WHOLE_FILE).provideDelegate(this, "runStrategySelection")

    var scriptPath: String?
        get() = myScriptPath.getValue(this)
        set(value) = myScriptPath.setValue(this, value)

    var selectedEnvironment: String?
        get() = mySelectedEnvironment.getValue(this)
        set(value) = mySelectedEnvironment.setValue(this, value)

    /**
     * 请求坐标（用于 SingleDeclaration 策略）
     */
    var coordinates: String?
        get() = myCoordinates.getValue(this)
        set(value) = myCoordinates.setValue(this, value)

    /**
     * 运行策略选择
     */
    var runStrategySelection: ScriptRunStrategySelection
        get() = myRunStrategySelection.getValue(this) ?: ScriptRunStrategySelection.WHOLE_FILE
        set(value) = myRunStrategySelection.setValue(this, value)

    // Environment variables are not persisted in options, they are loaded fresh each time from kttp.env.json
    @com.intellij.util.xmlb.annotations.Transient
    var environmentVariables: Map<String, String> = emptyMap()
}
