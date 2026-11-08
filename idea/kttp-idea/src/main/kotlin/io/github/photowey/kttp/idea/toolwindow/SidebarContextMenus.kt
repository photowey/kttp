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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import io.github.photowey.kttp.idea.gui.GuiDraft
import io.github.photowey.kttp.idea.gui.GuiDraftService
import io.github.photowey.kttp.idea.gui.GuiMode
import io.github.photowey.kttp.idea.toolwindow.CollectionsSidebar.DraftNode
import io.github.photowey.kttp.idea.toolwindow.CollectionsSidebar.FileNode
import io.github.photowey.kttp.idea.toolwindow.CollectionsSidebar.RequestNode
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtScriptInitializer
import java.io.IOException
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

/**
 * User commands own their confirmation and failure boundaries, then report successful changes.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal class SidebarContextMenus(
    private val project: Project,
    private val draftService: GuiDraftService,
    private val draftSelected: (GuiDraft) -> Unit,
    private val newDraft: () -> Unit,
    private val refresh: () -> Unit,
    private val pendingSelection: (String) -> Unit,
    private val clearSelection: () -> Unit,
    private val invalidate: (String) -> Unit,
    private val scriptChanged: (VirtualFile) -> Unit,
    private val reloadFile: (VirtualFile) -> Unit
) {
    fun addItems(menu: JPopupMenu, value: Any?) {
        when (value) {
            is DraftNode -> addDraftItems(menu, value.draft)
            is FileNode -> menu.add(item("删除") { deleteScript(value.file) })
            is RequestNode -> addRequestItems(menu, value)
        }
    }

    fun addDraftItems(menu: JPopupMenu, draft: GuiDraft) {
        menu.add(item("重命名 Draft") { draftOperation { renameDraft(draft) } })
        menu.add(item("复制 Draft") {
            draftOperation {
                val duplicated = draftService.duplicateDraft(draft.id) ?: return@draftOperation
                pendingSelection("draft:${duplicated.id}")
                draftSelected(duplicated)
                refresh()
            }
        })
        menu.add(item("删除 Draft") {
            draftOperation {
                if (!confirm("确定要删除 Draft \"${draft.title}\" 吗？", "删除 Draft")) return@draftOperation
                draftService.deleteDraft(draft.id)
                pendingSelection("group:DRAFTS")
                clearSelection()
                newDraft()
                refresh()
            }
        })
    }

    private fun renameDraft(draft: GuiDraft) {
        val name = Messages.showInputDialog(
            project, "请输入新的 Draft 名称", "重命名 Draft",
            Messages.getQuestionIcon(), draft.title, null
        ) ?: return
        val renamed = draftService.renameDraft(draft.id, name) ?: return
        pendingSelection("draft:${renamed.id}")
        draftSelected(renamed)
        refresh()
    }

    private fun draftOperation(action: () -> Unit) {
        try {
            action()
        } catch (error: IOException) {
            Messages.showErrorDialog(project, error.message ?: "Unable to update Draft storage", "Draft storage error")
        }
    }

    private fun deleteScript(file: VirtualFile) {
        if (!confirm("确定要删除 \"${file.name}\" 吗？此操作不可恢复。", "删除 Kttp 脚本")) return
        val path = file.path
        try {
            ApplicationManager.getApplication().runWriteAction { file.delete(project) }
        } catch (error: IOException) {
            Messages.showErrorDialog(project, "删除失败: ${error.message}", "删除 Kttp 脚本")
            return
        }
        invalidate(path)
        FileEditorManager.getInstance(project).closeFile(file)
        refresh()
    }

    private fun addRequestItems(menu: JPopupMenu, request: RequestNode) {
        val element = request.declaration.element
        if (element == null || !element.isValid) {
            Messages.showErrorDialog(project, "请求声明已失效，请刷新后重试。", "删除请求")
            return
        }
        val source = element.containingFile?.virtualFile ?: return
        menu.add(item("Open Script") { FileEditorManager.getInstance(project).openFile(source, true) })
        if (request.mode != GuiMode.GUI_READY) {
            menu.add(item("Quick Request details") {
                Messages.showInfoMessage(project, request.tooltip, "Quick Request support")
            })
            menu.addSeparator()
        }
        menu.add(item("删除") { deleteRequest(request, source) }.apply {
            isEnabled = deletableRequestStatement(element) != null
            if (!isEnabled) toolTipText =
                "This request is part of another expression. Use Open Script to edit it safely."
        })
    }

    private fun deleteRequest(request: RequestNode, source: VirtualFile) {
        if (!confirm("确定要从脚本中删除该请求（${request.displayMethod} ${request.displayUrl}）吗？", "删除请求")) return
        WriteCommandAction.runWriteCommandAction(project) {
            val statement =
                request.declaration.element?.let(::deletableRequestStatement) ?: return@runWriteCommandAction
            val document = FileDocumentManager.getInstance().getDocument(source) ?: return@runWriteCommandAction
            document.deleteString(statement.textRange.startOffset, statement.textRange.endOffset)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
        invalidate(source.path)
        scriptChanged(source)
        reloadFile(source)
    }

    private fun confirm(message: String, title: String): Boolean =
        Messages.showYesNoDialog(project, message, title, "删除", "取消", Messages.getQuestionIcon()) == Messages.YES

    private fun item(label: String, action: () -> Unit): JMenuItem =
        JMenuItem(label).apply { addActionListener { action() } }
}

/** Delete only complete request statements, leaving property initializers and wrappers to the script editor. */
internal fun deletableRequestStatement(call: KtCallExpression): KtExpression? {
    var statement: KtExpression = call
    while (true) {
        val parent = statement.parent as? KtDotQualifiedExpression ?: break
        if (parent.receiverExpression != statement || parent.selectorExpression !is KtCallExpression) return null
        statement = parent
    }
    return when (val parent = statement.parent) {
        is KtBlockExpression -> statement.takeIf { it in parent.statements }
        is KtScriptInitializer -> statement.takeIf { parent.body == it }
        else -> null
    }
}
