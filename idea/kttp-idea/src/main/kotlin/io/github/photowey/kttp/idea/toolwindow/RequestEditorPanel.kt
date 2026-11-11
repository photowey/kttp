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

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBScrollPane
import io.github.photowey.kttp.idea.KttpUiActions
import io.github.photowey.kttp.idea.cli.KttpCliInvocation
import io.github.photowey.kttp.idea.env.KttpEnvironmentService
import io.github.photowey.kttp.idea.execution.KttpExecutionResult
import io.github.photowey.kttp.idea.execution.RequestExecutionMode
import io.github.photowey.kttp.idea.execution.ScriptRequestExecutor
import io.github.photowey.kttp.idea.gui.*
import io.github.photowey.kttp.idea.path.KttpConsolesPath
import io.github.photowey.kttp.idea.script.KttpRequestDeclaration
import io.github.photowey.kttp.idea.script.toKttpScript
import java.awt.BorderLayout
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import org.jetbrains.kotlin.psi.KtFile

private val LOG = Logger.getInstance(RequestEditorPanel::class.java)

/**
 * Binds the request composer to one EDT-owned working session and coordinates explicit draft/script operations.
 * Runs HTTP or CLI work off EDT and checks identity/revision before applying completion metadata.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/21
 */
class RequestEditorPanel(
    private val project: Project,
    private val onResponseReceived: (KttpExecutionResult, String?) -> Unit
) : JPanel(BorderLayout()), Disposable {
    private val draftService = project.getService(GuiDraftService::class.java)
    private val executionPreferences = project.getService(RequestExecutionPreferences::class.java)
    private val scriptPreparation = ScriptExecutionPreparation(project)
    private var sourceDeclaration: KttpRequestDeclaration? = null
    private val headerPanel = ComposerHeaderPanel()
    private val bridgePanel = ComposerBridgePanel()
    private val paramsTabPanel = ParamsTabPanel()
    private val headersTabPanel = HeadersTabPanel()
    private val bodyTabPanel = BodyTabPanel()
    private val authTabPanel = AuthTabPanel()
    private val form = RequestEditorFormMapper(headerPanel, paramsTabPanel, headersTabPanel, bodyTabPanel, authTabPanel)
    private val scripts = RequestScriptFileCoordinator(project)
    private val projections = RequestEditorProjectionLoader(project)
    private val session = EditorWorkingSession(draftService.createEmptyDraft())
    private var isLoadingRequest = false
    private var activeCancellation: GuiRequestCancellation? = null
    private var disposed = false

    var currentSourceFile: VirtualFile?
        get() = session.sourceFile
        set(value) {
            session.sourceFile = value
        }
    var currentDeclarationIndex: Int?
        get() = session.declarationIndex
        set(value) {
            session.declarationIndex = value
        }
    internal val selectionRevision: Long get() = session.revision
    var onDraftsChanged: (() -> Unit)? = null
    var onScriptFileChanged: ((VirtualFile) -> Unit)? = null
    var onResponsePreview: ((KttpExecutionResult) -> Unit)? = null

    init {
        Disposer.register(project, this)
        setupHeaderActions()
        setupChangeListeners()
        val tabs = javax.swing.JTabbedPane().apply {
            addTab("Params", paramsTabPanel)
            addTab("Headers", headersTabPanel)
            addTab("Body", bodyTabPanel)
            addTab("Auth", authTabPanel)
        }
        val split = WorkspaceUi.split(
            false, "Kttp.ParametersScript.Proportion", 0.62f,
            WorkspaceUi.surface(tabs), WorkspaceUi.surface(bridgePanel)
        )
        val composer = JPanel(BorderLayout(0, com.intellij.util.ui.JBUI.scale(8))).apply {
            add(WorkspaceUi.surface(headerPanel, 10), BorderLayout.NORTH)
            add(split, BorderLayout.CENTER)
        }
        add(composer, BorderLayout.CENTER)
        createNewDraft()
    }

    fun createNewDraft() = loadDraft(draftService.createEmptyDraft())

    fun loadDraft(draft: GuiDraft) {
        if (draft.id == session.draft.id && !form.commitPendingEdits()) return
        bind {
            session.loadPersisted(
                draft.copy(
                    executionMode = executionPreferences.modeFor(
                        draft.id,
                        draft.executionMode
                    )
                )
            )
            sourceDeclaration = null
            form.fill(session.draft)
        }
        bridgePanel.focusForMode(session.draft.bridge.mode)
    }

    /** Loads a static script projection as an imported working copy; source changes remain explicit operations. */
    fun loadFromRequest(
        declaration: KttpRequestDeclaration?, sourceFile: VirtualFile? = null,
        declarationIndex: Int? = null, focusBridge: Boolean = true
    ) {
        if (declaration == null) {
            createNewDraft()
            return
        }
        try {
            val imported = projections.load(declaration, sourceFile, declarationIndex)
            bind {
                session.loadImported(
                    imported.copy(
                        executionMode = executionPreferences.modeFor(
                            imported.id,
                            imported.executionMode
                        )
                    ), sourceFile, declarationIndex
                )
                sourceDeclaration = declaration
                form.fill(session.draft)
            }
            if (focusBridge) bridgePanel.focusForMode(session.draft.bridge.mode)
        } catch (e: Exception) {
            LOG.warn("Failed to load request from script", e)
            createNewDraft()
        }
    }

    internal fun refreshCurrentSource() {
        val file = currentSourceFile ?: return
        val element = sourceDeclaration?.element
        val psi = PsiManager.getInstance(project).findFile(file) as? KtFile
        val declarations = psi?.toKttpScript()?.findRequestDeclarations().orEmpty()
        val index = if (element == null) -1 else declarations.indexOfFirst { it.element == element }
        if (index < 0) {
            sourceDeclaration = null
            applyDraftState()
            return
        }
        val mode = session.draft.executionMode
        loadFromRequest(declarations[index], file, index, focusBridge = false)
        session.selectExecutionMode(mode)
        executionPreferences.remember(session.draft.id, mode)
        bind { form.fill(session.draft) }
    }

    /**
     * Commits active cell edits, then appends the editable request to a chosen or new script and persists its link.
     * The write-safe UI operation is skipped if the selection or draft changes before it starts.
     */
    fun addCurrentRequestToScript(targetScript: VirtualFile?, onAdded: (VirtualFile) -> Unit) {
        if (!form.commitPendingEdits()) return
        if (!session.editable) {
            Messages.showInfoMessage(
                project,
                "Detach this imported request before adding it to a script.",
                "Request Is Read Only"
            )
            return
        }
        val draft = snapshot().copy(ownership = DraftOwnership.GUI_OWNED)
        if ((draft.request.target.url as? ValueRef.Literal)?.value?.isBlank() == true) {
            Messages.showInfoMessage(project, "Enter a URL before adding this request to a script.", "URL Required")
            return
        }
        editorOperation("Cannot Add Request") {
            val file = scripts.append(draft, targetScript)
            val linked = draft.copy(
                bridge = draft.bridge.copy(
                    sourceScriptPath = file.path,
                    generatedScriptPath = null, generatedScriptFingerprint = null
                )
            )
            val saved =
                if (targetScript == null) draftService.markGenerated(linked, file.path) else draftService.saveDraft(
                    linked
                )
            session.accept(saved)
            currentSourceFile = null
            currentDeclarationIndex = null
            applyDraftState()
            onScriptFileChanged?.invoke(file)
            onDraftsChanged?.invoke()
            onAdded(file)
        }
    }

    override fun dispose() {
        disposed = true
        activeCancellation?.cancel()
        activeCancellation = null
        headerPanel.requestProgress.dispose()
    }

    private fun bind(action: () -> Unit) {
        isLoadingRequest = true
        try {
            action()
        } finally {
            isLoadingRequest = false
            applyDraftState()
        }
    }

    private fun setupHeaderActions() {
        headerPanel.sendButton.addActionListener {
            activeCancellation?.let {
                it.cancel()
                headerPanel.requestProgress.cancelling()
                updateSendButton()
                return@addActionListener
            }
            executeDraftRequest()
        }
        headerPanel.saveDraftButton.addActionListener { saveDraft() }
        headerPanel.generateScriptButton.addActionListener { generateScript() }
        headerPanel.openScriptButton.addActionListener { editorOperation("Cannot Open Script") { scripts.open(session.linkedScriptPath) } }
        headerPanel.detachButton.addActionListener { detachIntoEditableDraft() }
        headerPanel.executionModeCombo.addActionListener {
            if (isLoadingRequest || activeCancellation != null) return@addActionListener
            if (!form.commitPendingEdits()) {
                bind { headerPanel.executionModeCombo.selectedItem = session.draft.executionMode }
                return@addActionListener
            }
            session.accept(snapshot())
            session.selectExecutionMode(headerPanel.executionModeCombo.selectedItem as RequestExecutionMode)
            executionPreferences.remember(session.draft.id, session.draft.executionMode)
            applyDraftState()
        }
    }

    private fun setupChangeListeners() {
        fun edited() {
            if (isLoadingRequest || !session.editable) return
            session.edit(snapshot())
            applyDraftState()
        }
        headerPanel.titleField.document.addDocumentListener(SimpleDocumentListener(::edited))
        headerPanel.requestNameField.document.addDocumentListener(SimpleDocumentListener(::edited))
        headerPanel.urlField.document.addDocumentListener(SimpleDocumentListener(::edited))
        headerPanel.methodComboBox.addActionListener { edited() }
        headerPanel.noRedirectCheck.addActionListener { edited() }
        headerPanel.noCookiesCheck.addActionListener { edited() }
        headerPanel.http2Check.addActionListener { edited() }
        paramsTabPanel.getTableModel().addTableModelListener { edited() }
        headersTabPanel.getTableModel().addTableModelListener { edited() }
        bodyTabPanel.addChangeListener(::edited)
        authTabPanel.addChangeListener(::edited)
    }

    private fun applyDraftState() {
        val draft = session.draft
        headerPanel.setEditable(session.editable)
        paramsTabPanel.setEditable(session.editable)
        headersTabPanel.setEditable(session.editable)
        bodyTabPanel.setEditable(session.editable)
        authTabPanel.setEditable(session.editable)
        headerPanel.executionModeCombo.isEnabled = activeCancellation == null
        val followScript = draft.executionMode == RequestExecutionMode.FOLLOW_SCRIPT
        headerPanel.executionHint.text = if (followScript) "Full script context." else "HTTP only; no then."
        headerPanel.executionModeCombo.toolTipText =
            if (followScript) "Complete script context, including client configuration and then." else "Direct HTTP only; script code and then are skipped."
        val summary =
            if (followScript) "The selected request runs with its complete script context." else when (draft.bridge.mode) {
                GuiMode.GUI_READY -> ""
                GuiMode.SEND_ONLY -> "Send this request here; edit its source script to make changes."
                GuiMode.SCRIPT_ONLY -> "Quick Request is unavailable; choose Follow Script to run the complete request."
            }
        headerPanel.updateModeBadge(draft.ownership, draft.bridge.mode, draft.meta.dirty, draft.executionMode)
        headerPanel.updateActionState(
            session.editable, draft.bridge.mode, draft.ownership, session.linkedScriptPath,
            draft.bridge.generatedScriptPath != null
        )
        updateSendButton()
        refreshBridgePanel(summary)
    }

    private fun updateSendButton() {
        val running = activeCancellation != null
        val reason = executionBlockReason()
        val cancelling = activeCancellation?.isCancelled == true
        headerPanel.sendButton.isEnabled = !disposed && !cancelling && (running || reason == null)
        headerPanel.sendButton.text = if (running) "Cancel" else "Send"
        headerPanel.sendButton.icon = if (running) AllIcons.Process.Stop else AllIcons.Actions.Execute
        headerPanel.executionModeCombo.isEnabled = !running
        headerPanel.sendButton.toolTipText = when {
            cancelling -> "Waiting for the active request or script process to stop."
            running -> "Cancel the active request or script process."
            reason != null -> reason
            session.draft.executionMode == RequestExecutionMode.FOLLOW_SCRIPT -> "Run the selected request in its complete script context."
            else -> "Send the displayed HTTP request directly; script code and then are skipped."
        }
    }

    private fun executionBlockReason(): String? {
        val draft = session.draft
        if (draft.executionMode == RequestExecutionMode.FOLLOW_SCRIPT && draft.ownership == DraftOwnership.SCRIPT_IMPORTED) {
            return if (currentSourceFile?.isValid != true || sourceDeclaration?.element == null)
                "The source request is unavailable. Select it again before sending." else null
        }
        if (draft.executionMode == RequestExecutionMode.QUICK_REQUEST && draft.bridge.mode == GuiMode.SCRIPT_ONLY)
            return draft.bridge.gaps.firstOrNull()?.message
                ?: "Quick Request cannot represent this script. Choose Follow Script."
        return if (headerPanel.urlField.text.isBlank()) "Enter a URL before sending." else null
    }

    private fun saveDraft() {
        if (!form.commitPendingEdits()) return
        editorOperation("Cannot Save Draft") {
            session.accept(draftService.saveDraft(snapshot()))
            applyDraftState()
            onDraftsChanged?.invoke()
        }
    }

    private fun generateScript() {
        if (!form.commitPendingEdits()) return
        editorOperation("Cannot Generate Script") {
            val draft = snapshot().copy(ownership = DraftOwnership.GUI_OWNED)
            val file = scripts.generate(draft)
            session.accept(draftService.markGenerated(draft, file.path))
            applyDraftState()
            onDraftsChanged?.invoke()
            onScriptFileChanged?.invoke(file)
        }
    }

    private fun detachIntoEditableDraft() {
        if (!form.commitPendingEdits()) return
        session.accept(snapshot())
        session.detach()
        bind { form.fill(session.draft) }
    }

    /**
     * Commits form edits and prepares the selected execution path on EDT, then runs it on a pooled thread.
     * The captured draft ID/revision protects the working copy when completion returns to EDT.
     */
    private fun executeDraftRequest() {
        if (disposed || activeCancellation != null) return
        if (executionBlockReason() != null) {
            updateSendButton()
            return
        }
        if (!form.commitPendingEdits()) return
        val started = System.nanoTime()
        val cancellation = GuiRequestCancellation()
        activeCancellation = cancellation
        headerPanel.requestProgress.start(session.draft.executionMode)
        updateSendButton()
        val draftId = session.draft.id
        val revision = session.revision
        KttpUiActions.submit(project) {
            if (disposed || activeCancellation !== cancellation) return@submit
            try {
                check(session.draft.id == draftId && session.revision == revision) {
                    "The request changed before execution started. Send it again."
                }
                check(!cancellation.isCancelled) { "Request cancelled" }
                launchDraftRequest(started, cancellation)
            } catch (failure: Exception) {
                activeCancellation = null
                headerPanel.requestProgress.finish()
                updateSendButton()
                onResponseReceived(
                    KttpExecutionResult(
                        session.draft.title, false, failure.message ?: "Cannot prepare request",
                        executionMode = session.draft.executionMode,
                        totalTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                    ), session.linkedScriptPath
                )
            }
        }
    }

    private fun prepareScript(): PreparedScriptRequest? {
        if (session.draft.executionMode != RequestExecutionMode.FOLLOW_SCRIPT) return null
        return scriptPreparation.prepare(snapshot(), currentSourceFile, sourceDeclaration).also { ready ->
            bind {
                session.accept(ready.draft)
                if (ready.draft.ownership == DraftOwnership.SCRIPT_IMPORTED) currentDeclarationIndex = ready.declarationIndex
                form.fill(session.draft)
            }
            if (ready.draft.ownership != DraftOwnership.SCRIPT_IMPORTED) {
                onScriptFileChanged?.invoke(ready.file)
                onDraftsChanged?.invoke()
            }
        }
    }

    private fun launchDraftRequest(started: Long, cancellation: GuiRequestCancellation) {
        val prepared = prepareScript()
        val draft = snapshot()
        val revision = session.revision
        val linkedPath = prepared?.file?.path ?: session.linkedScriptPath
        val environmentFile =
            prepared?.file ?: currentSourceFile ?: linkedPath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        val environment = KttpEnvironmentService.getInstance(project).getEnvironmentVariables(environmentFile)
        val baseDir = project.basePath ?: environmentFile?.parent?.path
        val responseDir = if (prepared != null) KttpConsolesPath.getResponsesDir()
            .toPath() else GuiRequestExecutor.resolveResponseDir(project.basePath)
        val invocation = prepared?.let {
            KttpCliInvocation(
                it.file.path,
                requestLines = it.requestLines,
                envVariables = environment,
                jsonOutput = true,
                responseDir = responseDir.toString(),
                baseDir = project.basePath
            )
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            val preview: (KttpExecutionResult) -> Unit = { response ->
                ApplicationManager.getApplication().invokeLater {
                    if (!disposed && !project.isDisposed && activeCancellation === cancellation && !cancellation.isCancelled) {
                        onResponsePreview?.invoke(response.copy(scriptName = draft.title, executionMode = draft.executionMode))
                    }
                }
            }
            val result = try {
                if (invocation != null) ScriptRequestExecutor.execute(project, invocation, cancellation, preview)
                else GuiRequestExecutor.execute(draft, environment, baseDir, responseDir, cancellation, preview)
            } catch (e: Exception) {
                KttpExecutionResult(scriptName = draft.title, success = false, message = e.message ?: "Request failed")
            }.copy(
                scriptName = draft.title, executionMode = draft.executionMode,
                totalTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            )
            ApplicationManager.getApplication().invokeLater {
                completeExecution(cancellation, draft, revision, result, linkedPath)
            }
        }
    }

    /**
     * Publishes a current execution result on EDT; draft metadata changes only if its ID and revision still match.
     * A result can still be displayed after the user edits or selects a different request.
     */
    private fun completeExecution(
        cancellation: GuiRequestCancellation, sent: GuiDraft, revision: Long,
        result: KttpExecutionResult, linkedPath: String?
    ) {
        if (disposed || project.isDisposed || activeCancellation !== cancellation) return
        activeCancellation = null
        headerPanel.requestProgress.finish()
        if (session.stillMatches(sent, revision)) {
            editorOperation("Cannot Save Request Result") {
                val completed =
                    session.draft.copy(request = sent.request, meta = session.draft.meta.copy(dirty = false))
                session.accept(draftService.markSent(completed, result.responseFilePath))
                if (session.draft.ownership == DraftOwnership.GUI_OWNED) onDraftsChanged?.invoke()
            }
        }
        applyDraftState()
        onResponseReceived(result, linkedPath)
        KttpToolWindowPanel.refreshResponseTreeIfVisible(project)
    }

    private fun snapshot(): GuiDraft = form.read(session.draft, currentSourceFile, currentDeclarationIndex)

    private fun refreshBridgePanel(summary: String) {
        val draft = session.draft
        val buckets = GuiBridgeFormatter.bucketGaps(draft.bridge.gaps)
        bridgePanel.update(
            summary, GuiBridgeFormatter.formatSourceSummary(draft.request),
            GuiBridgeFormatter.formatGaps(buckets.blocking, "No blocking gaps."),
            GuiBridgeFormatter.formatGaps(buckets.advisory, "No advisory gaps."), session.linkedScriptPath,
            runCatching { GuiScriptGenerator.render(draft) }.getOrElse { "Script preview unavailable: ${it.message}" })
    }

    private fun editorOperation(title: String, action: () -> Unit) {
        val draftId = session.draft.id
        val revision = session.revision
        KttpUiActions.submit(project) {
            if (disposed || session.draft.id != draftId || session.revision != revision) return@submit
            try {
                action()
            } catch (e: Exception) {
                LOG.warn(title, e)
                Messages.showErrorDialog(project, e.message ?: title, title)
                applyDraftState()
            }
        }
    }
}

/**
 * Forwards document insertion, removal, and attribute changes to one callback.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/21
 */
private class SimpleDocumentListener(private val onChange: () -> Unit) : javax.swing.event.DocumentListener {
    override fun insertUpdate(e: javax.swing.event.DocumentEvent) = onChange()
    override fun removeUpdate(e: javax.swing.event.DocumentEvent) = onChange()
    override fun changedUpdate(e: javax.swing.event.DocumentEvent) = onChange()
}
