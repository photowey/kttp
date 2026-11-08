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

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.LabeledComponent
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * Editor UI for Kttp Run Configuration (script path).
 * Works with KttpRunConfiguration.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpRunConfigurationEditor : SettingsEditor<KttpRunConfiguration>() {

    private val scriptField = JTextField(40)

    private val panel = JPanel(BorderLayout()).apply {
        add(LabeledComponent.create(scriptField, "Script file (.kttp.kts) path"), BorderLayout.NORTH)
    }

    override fun resetEditorFrom(s: KttpRunConfiguration) {
        scriptField.text = s.scriptPath ?: ""
    }

    override fun applyEditorTo(s: KttpRunConfiguration) {
        s.scriptPath = scriptField.text.takeIf { it.isNotBlank() }
    }

    override fun createEditor(): JComponent = panel
}
