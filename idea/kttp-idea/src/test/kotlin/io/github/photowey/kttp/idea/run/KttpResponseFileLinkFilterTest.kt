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

import com.intellij.execution.filters.HyperlinkWithPopupMenuInfo
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import java.awt.event.MouseEvent
import javax.swing.JPanel

/**
 * Verifies response links preserve path offsets, editor navigation and the public reveal-file menu.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/11
 */
class KttpResponseFileLinkFilterTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()

    fun testResponsePathOpensEditorAndProvidesRevealAction() {
        val file = myFixture.addFileToProject("response with spaces.json", "{}").virtualFile
        val filter = KttpResponseFileLinkFilter(project)
        val prefix = "Response file saved.\r\n"
        val line = "> ${file.path}\r\n"
        assertNull(filter.applyFilter(prefix, prefix.length))
        val result = requireNotNull(filter.applyFilter(line, prefix.length + line.length))
        assertEquals(prefix.length + 2, result.resultItems.single().highlightStartOffset)
        assertEquals(prefix.length + line.length - 2, result.resultItems.single().highlightEndOffset)
        val link = result.resultItems.single().hyperlinkInfo as HyperlinkWithPopupMenuInfo
        val event = MouseEvent(JPanel(), MouseEvent.MOUSE_RELEASED, 0, 0, 0, 0, 1, true)
        val action = requireNotNull(link.getPopupMenuGroup(event)).getChildren(null as AnActionEvent?).single()
        assertEquals(RevealFileAction.getActionName(), action.templatePresentation.text)
        link.navigate(project)
        PlatformTestUtil.waitWithEventsDispatching(
            "Response opened in editor", { FileEditorManager.getInstance(project).isFileOpen(file) }, 10
        )
    }

    fun testUnrelatedAndEmptyOutputDoNotCreateResponseLinks() {
        val filter = KttpResponseFileLinkFilter(project)
        assertNull(filter.applyFilter("> /unrelated.json\n", 18))
        assertNull(filter.applyFilter("Response file saved.\n", 40))
        assertNull(filter.applyFilter("> \n", 43))
        assertNull(filter.applyFilter("> /not-immediately-after-marker.json\n", 80))
    }
}
