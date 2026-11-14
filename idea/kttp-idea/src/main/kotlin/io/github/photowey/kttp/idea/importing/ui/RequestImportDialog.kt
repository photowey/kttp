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

package io.github.photowey.kttp.idea.importing.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.github.photowey.kttp.idea.importing.ImportBundle
import io.github.photowey.kttp.idea.importing.ImportCommitResult
import io.github.photowey.kttp.idea.importing.ImportDestination
import io.github.photowey.kttp.idea.importing.ImportFormat
import io.github.photowey.kttp.idea.importing.ImportPreparation
import io.github.photowey.kttp.idea.importing.ImportSeverity
import io.github.photowey.kttp.idea.importing.RequestImportCoordinator
import io.github.photowey.kttp.idea.importing.RequestImportRegistry
import java.awt.Component
import java.awt.Container
import java.awt.event.ActionEvent
import java.util.concurrent.CancellationException
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingWorker
import javax.swing.Timer
import javax.swing.table.AbstractTableModel

/**
 * Reviews one immutable import snapshot before committing requests or environment profiles.
 * Parsing, source file reads and script generation run off the EDT; stale workers are discarded.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
class RequestImportDialog(
    private val project: Project,
    private val destination: ImportDestination,
    private val onImported: (ImportCommitResult) -> Unit = {},
    initialFormat: ImportFormat = ImportFormat.CURL
) : DialogWrapper(project, true) {
    private val coordinator = RequestImportCoordinator(project)
    private val importState = ImportDialogState()
    private val source = ImportSourceControls(project, initialFormat)
    private val layout = ImportDialogLayout()
    internal val destinationControls = ImportDestinationControls(project, destination)
    internal val environmentSelection = ImportEnvironmentSelection(project)
    private val status = JLabel("Choose a source, then refresh the preview.")
    private val target = readOnlyArea(1).apply {
        font = UIUtil.getLabelFont(); columns = 0; isOpaque = false
        foreground = UIUtil.getContextHelpForeground()
        accessibleContext.accessibleName = "Import destination summary"
    }
    private val script = readOnlyArea(10)
    private val diagnostics = readOnlyArea(6)
    private val retainedSources = readOnlyArea(6)
    private val rows = RequestRows(importState, ::selectionChanged)
    private val requests = JBTable(rows).apply {
        fillsViewportHeight = true
        columnModel.getColumn(0).apply {
            minWidth = JBUI.scale(64)
            preferredWidth = minWidth
            maxWidth = JBUI.scale(72)
        }
        columnModel.getColumn(1).preferredWidth = JBUI.scale(160)
        columnModel.getColumn(2).apply {
            minWidth = JBUI.scale(80)
            preferredWidth = minWidth
            maxWidth = JBUI.scale(96)
        }
        columnModel.getColumn(3).preferredWidth = JBUI.scale(300)
        columnModel.getColumn(4).preferredWidth = JBUI.scale(160)
        accessibleContext.accessibleName = "Requests to import"
    }
    private val environmentFolder = JButton("Choose Environment Folder…").apply {
        isEnabled = false
        isVisible = false
        toolTipText = "Available when importing only an environment"
        addActionListener {
            FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFolderDescriptor(), project,
                ImportSourceLocations.initialSelection(project, environmentDirectory)
            ) {
                environmentDirectory = it.path
                invalidatePreview("Target changed. Refresh the preview.")
                updateTarget()
            }
        }
    }
    private val cancelPreview = JButton("Cancel Preview").apply {
        isEnabled = false
        addActionListener { invalidatePreview("Preview cancelled. No requests were imported.") }
    }
    private val refreshAction = object : DialogWrapperAction("Refresh Preview") {
        override fun doAction(e: ActionEvent?) = refreshPreview()
    }
    private var environmentDirectory: String? = null
    private var worker: SwingWorker<*, *>? = null
    private var committing = false
    private var previewBaseDirectory: String? = null
    private var previewTarget: String? = null
    private var observedTarget = targetFingerprint()
    private val targetWatcher = Timer(400) {
        val current = targetFingerprint()
        if (current != observedTarget && !committing) {
            observedTarget = current
            invalidatePreview("Target changed. Refresh the preview.")
        }
        updateImportGate()
    }

    init {
        title = "Import Requests"
        setOKButtonText("Import")
        init()
        source.onEdited = {
            environmentDirectory = null
            invalidatePreview("Source changed. Refresh the preview.")
            updateTarget()
        }
        destinationControls.onChanged = {
            environmentDirectory = null
            invalidatePreview("Destination changed. Refresh the preview.")
            updateTarget()
        }
        updateTarget()
        updateImportGate()
        targetWatcher.start()
        Disposer.register(disposable, Disposable {
            targetWatcher.stop()
            worker?.cancel(true)
            importState.cancel()
        })
    }

    override fun createCenterPanel(): JComponent = layout.create(
        source, requests, target, environmentFolder, refreshAction, cancelPreview, status,
        script, diagnostics, retainedSources, destinationControls, environmentSelection
    )

    override fun createActions(): Array<Action> = arrayOf(okAction, cancelAction)

    override fun getPreferredFocusedComponent(): JComponent = source.preferredFocus()

    override fun getDimensionServiceKey(): String = "Kttp.RequestImport.Dialog"

    override fun doOKAction() {
        if (committing) return
        val targetError = destinationError()
        if (targetError != null || previewTarget != targetFingerprint()) {
            invalidatePreview(targetError ?: "Target changed. Refresh the preview.")
            return
        }
        val bundle = importState.bundle ?: return
        val selected = importState.selected.toSet()
        val token = importState.beginCommit() ?: return
        val importDestination = effectiveDestination()
        val directory = previewBaseDirectory
        val modality = ModalityState.current()
        committing = true
        worker?.cancel(true)
        enableTree(source, false)
        enableTree(destinationControls, false)
        enableTree(environmentSelection, false)
        refreshAction.isEnabled = false
        cancelAction.isEnabled = false
        cancelPreview.isEnabled = false
        environmentFolder.isEnabled = false
        requests.isEnabled = false
        updateImportGate()
        status.text = "Importing…"
        worker = object : SwingWorker<ImportCommitResult, Unit>() {
            override fun doInBackground(): ImportCommitResult =
                coordinator.commit(bundle, selected, importDestination, directory, modality)

            override fun done() {
                if (!importState.isCurrent(token)) return
                val result = try {
                    get()
                } catch (_: CancellationException) {
                    commitFailed(token, "Import cancelled before completion. Refresh the preview.")
                    return
                } catch (failure: Exception) {
                    commitFailed(
                        token,
                        safeFailure(failure, "Import failed. Refresh the preview and review the destination.")
                    )
                    return
                }
                completeImport(result, modality)
            }
        }.also { it.execute() }
    }

    /** SwingWorker completion must re-enter the captured IDE modality before restoring source PSI. */
    internal fun completeImport(result: ImportCommitResult, modality: ModalityState) {
        ApplicationManager.getApplication().invokeLater({
            committing = false
            try {
                if (!project.isDisposed) {
                    environmentSelection.apply(result)
                    onImported(result)
                    result.files.firstOrNull { it.name.endsWith(".kttp.kts") }?.takeIf {
                        effectiveDestination() is ImportDestination.NewScript
                    }?.let { io.github.photowey.kttp.idea.path.KttpFileNavigation.open(project, it) }
                }
            } finally {
                close(OK_EXIT_CODE)
            }

        }, modality)
    }

    override fun doCancelAction() {
        if (committing) return
        importState.cancel()
        worker?.cancel(true)
        super.doCancelAction()
    }

    private fun refreshPreview() {
        if (committing) return
        destinationError()?.let { invalidatePreview(it); return }
        worker?.cancel(true)
        val snapshot = source.snapshot()
        val importDestination = effectiveDestination()
        val fingerprint = targetFingerprint()
        val token = importState.beginPreview()
        clearPreview()
        status.text = "Reading source and preparing preview…"
        cancelPreview.isEnabled = true
        updateImportGate()
        worker = object : SwingWorker<Preview, Unit>() {
            override fun doInBackground(): Preview {
                val input = snapshot.read()
                val bundle = ImportPreparation.review(
                    RequestImportRegistry.parser(snapshot.format).parse(input), input.baseDirectory,
                    (importDestination as? ImportDestination.Script)?.originalText.orEmpty()
                )
                val selected = bundle.requests.indices.filter { bundle.requests[it].selectedByDefault }.toSet()
                val text = generatePreview(bundle, selected, input.baseDirectory, importDestination)
                return Preview(bundle, text, input.baseDirectory)
            }

            override fun done() {
                if (!importState.isCurrent(token) || fingerprint != targetFingerprint()) return
                cancelPreview.isEnabled = false
                try {
                    val value = get()
                    if (!importState.acceptPreview(token, value.bundle)) return
                    previewBaseDirectory = value.baseDirectory
                    destinationControls.suggest(value.bundle)
                    environmentSelection.configure(value.bundle)
                    observedTarget = targetFingerprint()
                    previewTarget = observedTarget
                    script.text = value.script
                    script.caretPosition = 0
                    showDiagnostics(value.bundle)
                    rows.fireTableDataChanged()
                    updateTarget()
                    updateImportGate()
                    status.text = when {
                        value.bundle.diagnostics.any { it.severity == ImportSeverity.ERROR } -> "Resolve the source errors before importing."
                        value.bundle.requests.isEmpty() && value.bundle.environments.isEmpty() -> "No requests or environments found."
                        else -> "${value.bundle.requests.size} request(s), ${value.bundle.environments.size} environment(s). Review diagnostics and select requests."
                    }
                } catch (_: CancellationException) {
                    previewFailed(token, "Preview cancelled.")
                } catch (failure: Exception) {
                    previewFailed(
                        token,
                        safeFailure(failure, "Could not prepare the preview. Check the source file and input.")
                    )
                }
            }
        }.also { it.execute() }
    }

    private fun selectionChanged(index: Int, checked: Boolean) {
        if (!importState.select(index, checked)) return
        worker?.cancel(true)
        rows.fireTableRowsUpdated(index, index)
        val bundle = importState.bundle ?: return
        val selected = importState.selected.toSet()
        val directory = previewBaseDirectory
        val importDestination = effectiveDestination()
        val fingerprint = targetFingerprint()
        val token = importState.beginSelectionPreview()
        cancelPreview.isEnabled = true
        updateImportGate()
        status.text = "Updating selected request preview…"
        worker = object : SwingWorker<String, Unit>() {
            override fun doInBackground(): String = generatePreview(bundle, selected, directory, importDestination)
            override fun done() {
                if (!importState.isCurrent(token) || fingerprint != targetFingerprint()) return
                cancelPreview.isEnabled = false
                try {
                    val text = get()
                    if (!importState.acceptSelectionPreview(token)) return
                    script.text = text
                    script.caretPosition = 0
                    status.text = "${selected.size} request(s) selected. Review the preview before importing."
                    updateImportGate()
                } catch (_: CancellationException) {
                    previewFailed(token, "Preview cancelled.")
                } catch (failure: Exception) {
                    previewFailed(
                        token,
                        safeFailure(failure, "Could not generate this selection. Refresh the preview.")
                    )
                }
            }
        }.also { it.execute() }
    }

    private fun generatePreview(
        bundle: ImportBundle,
        selected: Set<Int>,
        directory: String?,
        target: ImportDestination
    ): String =
        if (bundle.diagnostics.any { it.severity == ImportSeverity.ERROR } ||
            (selected.isEmpty() && bundle.environments.isEmpty())) ""
        else coordinator.preview(bundle, selected, directory, target)

    private fun invalidatePreview(message: String) {
        if (committing) return
        worker?.cancel(true)
        importState.invalidate()
        previewTarget = null
        observedTarget = targetFingerprint()
        clearPreview()
        cancelPreview.isEnabled = false
        environmentFolder.isEnabled = false
        status.text = message
        updateImportGate()
    }

    private fun clearPreview() {
        script.text = ""
        diagnostics.text = ""
        retainedSources.text = ""
        rows.fireTableDataChanged()
    }

    private fun previewFailed(token: Long, message: String) {
        if (!importState.fail(token, message)) return
        script.text = ""
        status.text = message
        updateImportGate()
    }

    private fun commitFailed(token: Long, message: String) {
        committing = false
        importState.fail(token, message)
        enableTree(source, true)
        enableTree(destinationControls, true)
        enableTree(environmentSelection, true)
        refreshAction.isEnabled = true
        cancelAction.isEnabled = true
        requests.isEnabled = true
        status.text = message
        updateImportGate()
    }

    private fun showDiagnostics(bundle: ImportBundle) {
        val all = (bundle.diagnostics + bundle.requests.flatMap { it.diagnostics }).distinct()
        diagnostics.text = all.joinToString("\n\n") {
            "${it.severity} · ${it.location}\n${it.message}" + if (it.requiresConsent) "\nSelect this request explicitly to accept static fields only." else ""
        }.ifEmpty { "No diagnostics." }
        retainedSources.text = all.filter { it.retainedSource != null }.joinToString("\n\n") {
            "${it.location}\n${it.retainedSource}"
        }.ifEmpty { "No scripts or unsupported source fragments were retained." }
        diagnostics.caretPosition = 0
        retainedSources.caretPosition = 0
    }

    private fun effectiveDestination(): ImportDestination = environmentDirectory?.let {
        ImportDestination.EnvironmentDirectory(it)
    } ?: destinationControls.destination()

    private fun updateTarget() {
        val selected = effectiveDestination()
        target.text = when (selected) {
            is ImportDestination.NewScript -> "Environment file: kttp.env.json beside the script."
            is ImportDestination.Script -> "Current script · Environment file saved beside it."
            ImportDestination.Drafts -> "Saved drafts · Sources → Drafts"
            is ImportDestination.EnvironmentDirectory -> "Environment file: kttp.env.json"
        }
        target.toolTipText = coordinator.describeTarget(selected)
        target.caretPosition = 0
        val bundle = importState.bundle
        environmentFolder.isVisible = bundle != null && bundle.requests.isEmpty() && bundle.environments.isNotEmpty()
        environmentFolder.isEnabled =
            !committing && bundle != null && bundle.requests.isEmpty() && bundle.environments.isNotEmpty()
    }

    private fun updateImportGate() {
        layout.setBusy(committing || cancelPreview.isEnabled)
        isOKActionEnabled =
            importState.canImport && !committing && destinationError() == null && previewTarget == targetFingerprint()
    }

    private fun destinationError(): String? {
        if (project.isDisposed) return "The project is closed."
        destinationControls.error()?.let { return it }
        val selectedDestination = effectiveDestination()
        if (selectedDestination is ImportDestination.Script) {
            if (selectedDestination.editor.isDisposed || !selectedDestination.file.isValid ||
                !FileEditorManager.getInstance(project).isFileOpen(selectedDestination.file)
            ) return "The target editor is closed. Reopen Import Requests from the target script."
            if (!selectedDestination.file.isWritable || !selectedDestination.editor.document.isWritable) return "The target script is read-only."
            if (selectedDestination.editor.document.text != selectedDestination.originalText) return "The target script changed. Reopen Import Requests from the target script."
        }
        return null
    }

    private fun targetFingerprint(): String {
        val selected = effectiveDestination()
        return when (selected) {
            is ImportDestination.Script -> if (selected.editor.isDisposed) "disposed:${selected.file.path}" else
                "${selected.file.path}:${selected.file.isValid}:${selected.file.isWritable}:" +
                    "${selected.editor.document.modificationStamp}:${selected.editor.caretModel.offset}"

            else -> "${project.isDisposed}:$selected"
        }
    }

    // Coordinator messages are intended for the user; I/O and parser exception details can contain source credentials.
    private fun safeFailure(failure: Exception, fallback: String): String {
        val cause = failure.cause ?: failure
        return if (cause is IllegalStateException) cause.message?.takeIf { it.length <= 600 } ?: fallback else fallback
    }

    /**
     * One completed background preview, with the resolved base directory for later commit.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private data class Preview(val bundle: ImportBundle, val script: String, val baseDirectory: String?)
}
