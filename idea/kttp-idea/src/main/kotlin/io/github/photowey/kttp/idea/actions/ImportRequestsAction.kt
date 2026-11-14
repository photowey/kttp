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

package io.github.photowey.kttp.idea.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import io.github.photowey.kttp.idea.importing.ImportDestination
import io.github.photowey.kttp.idea.importing.ImportFormat
import io.github.photowey.kttp.idea.importing.ui.RequestImportDialog
import io.github.photowey.kttp.idea.lang.KttpFileType

/**
 * Opens the shared import review for the active Kttp script.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
open class ImportRequestsAction(private val initialFormat: ImportFormat = ImportFormat.CURL) : AnAction(
    "Import Requests…", "Import cURL or Postman requests into the current Kttp script", AllIcons.Actions.Download
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: e.getData(CommonDataKeys.PSI_FILE)?.virtualFile ?: return
        if (editor.isDisposed || !file.isValid || !file.name.endsWith("." + KttpFileType.INSTANCE.defaultExtension)) return
        RequestImportDialog(
            project,
            ImportDestination.Script(editor, file, editor.document.text),
            initialFormat = initialFormat
        ).show()
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: e.getData(CommonDataKeys.PSI_FILE)?.virtualFile
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = e.project != null && file?.isValid == true && editor != null &&
            !editor.isDisposed && file.name.endsWith("." + KttpFileType.INSTANCE.defaultExtension)
        e.presentation.isEnabled =
            e.presentation.isEnabled && file?.isWritable == true && editor?.document?.isWritable == true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
