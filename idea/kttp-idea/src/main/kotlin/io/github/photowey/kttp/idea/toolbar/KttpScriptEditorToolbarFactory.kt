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

package io.github.photowey.kttp.idea.toolbar

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.util.ui.JBUI
import io.github.photowey.kttp.idea.env.ChooseEnvironmentAction
import javax.swing.JPanel

/**
 * Creates the editor toolbar for .kttp.kts files.
 * The left side contains Add Request, Run All Requests, Copy as cURL, and Environment;
 * the right side contains the action to create a new Kttp script.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/16
 */
class KttpScriptEditorToolbarFactory(private val fileEditor: FileEditor) {

    fun create(): JPanel {
        return JBUI.Panels.simplePanel()
            .addToLeft(createLeftToolbar().component)
            .addToRight(createRightToolbar().component)
    }

    private fun createLeftToolbar(): ActionToolbar {
        val group = DefaultActionGroup().apply {
            // Add Request actions (dropdown group)
            addAction(ActionManager.getInstance().getAction("kttp.addRequest.group"))
            addSeparator()

            // Run file action
            addAction(ActionManager.getInstance().getAction(RunKttpFileAction.ID))
            addSeparator()

            // Copy as cURL action
            addAction(ActionManager.getInstance().getAction("kttp.copyAsCurl"))
            addSeparator()

            // Environment selector
            add(ChooseEnvironmentAction())
        }

        return createToolbar(PLACE, group)
    }

    private fun createRightToolbar(): ActionToolbar {
        val group = DefaultActionGroup().apply {
            // Create a new script in Scratches and Consoles
            addAction(ActionManager.getInstance().getAction("io.github.photowey.kttp.NewKttpScript"))
        }

        return createToolbar("$PLACE Right", group)
    }

    private fun createToolbar(place: String, group: DefaultActionGroup): ActionToolbar {
        return ActionManager.getInstance().createActionToolbar(place, group, true).apply {
            setTargetComponent(fileEditor.component)
        }
    }

    /**
     * Defines the action-system place assigned to the script editor toolbar.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/16
     */
    companion object {
        const val PLACE = "KttpEditorToolbar"
    }
}
