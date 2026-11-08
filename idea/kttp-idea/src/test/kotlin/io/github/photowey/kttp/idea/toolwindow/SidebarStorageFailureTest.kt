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

import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.ui.TestInputDialog
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.photowey.kttp.idea.gui.GuiDraftService
import io.github.photowey.kttp.idea.path.KttpProjectPath
import java.io.File
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

/**
 * Verifies failed draft persistence does not publish editor or selection changes.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class SidebarStorageFailureTest : BasePlatformTestCase() {
    fun testFailedDraftMutationsLeaveEditorAndSelectionCallbacksUntouched() {
        val service = project.getService(GuiDraftService::class.java)
        val draft = service.saveDraft(service.createEmptyDraft().copy(title = "Keep working copy"))
        val archive = File(KttpProjectPath.getGuiStateDir(requireNotNull(project.basePath)), "drafts.json")
        val original = archive.readBytes()
        var selections = 0
        var resets = 0
        var refreshes = 0
        val pending = mutableListOf<String>()
        val menus = SidebarContextMenus(
            project, service, { selections++ }, { resets++ }, { refreshes++ },
            { pending += it }, { resets++ }, {}, {}, {})
        var errors = 0
        val oldDialog = TestDialogManager.setTestDialog(TestDialog { message ->
            if (message.contains("Invalid draft archive")) errors++
            Messages.YES
        })
        val oldInput = TestDialogManager.setTestInputDialog(TestInputDialog { "Renamed" })
        try {
            archive.writeText("{broken draft storage")
            for (label in listOf("重命名 Draft", "复制 Draft", "删除 Draft")) {
                val menu = JPopupMenu()
                menus.addDraftItems(menu, draft)
                menu.components.filterIsInstance<JMenuItem>().single { it.text == label }.doClick()
            }

            assertEquals(3, errors)
            assertEquals(0, selections)
            assertEquals(0, resets)
            assertEquals(0, refreshes)
            assertTrue(pending.isEmpty())
            assertEquals("{broken draft storage", archive.readText())
            assertEquals(draft, service.findDraft(draft.id))
        } finally {
            archive.writeBytes(original)
            service.deleteDraft(draft.id)
            TestDialogManager.setTestDialog(oldDialog)
            TestDialogManager.setTestInputDialog(oldInput)
        }
    }
}
