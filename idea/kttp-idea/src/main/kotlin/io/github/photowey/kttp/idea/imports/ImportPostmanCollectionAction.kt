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

package io.github.photowey.kttp.idea.imports

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import io.github.photowey.kttp.idea.icons.KttpIcons

/**
 * Placeholder: Import Postman collection as .kttp.kts.
 * Full implementation would parse Postman collection and generate Kttp DSL.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/16
 */
class ImportPostmanCollectionAction : AnAction(
    "Import Postman Collection",
    "Convert Postman collection to Kttp script (placeholder)",
    KttpIcons.File
) {

    override fun actionPerformed(e: AnActionEvent) {
        Messages.showInfoMessage(
            e.project,
            "Import from Postman collection is not yet implemented.\n\n" +
                "Workaround: Create a new Kttp Script and paste your requests manually.",
            "Import Postman Collection - Not Implemented"
        )
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = false
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
