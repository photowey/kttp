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

package io.github.photowey.kttp.idea.importing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import io.github.photowey.kttp.idea.execution.RequestExecutionMode
import io.github.photowey.kttp.idea.gui.*
import io.github.photowey.kttp.idea.path.KttpConsolesPath
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import java.util.UUID

/**
 * Converts selected requests in the background, then commits a checked target under a short IDE command.
 * Parser strategies and destination persistence remain independent.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
class RequestImportCoordinator(private val project: Project) {
    fun preview(
        bundle: ImportBundle, selected: Set<Int>, baseDirectory: String?,
        destination: ImportDestination = ImportDestination.Drafts
    ): String {
        validate(bundle, selected)
        return renderSelection(bundle, selected, destination, baseDirectory).joinToString("\n") { it.second }
    }

    fun describeTarget(destination: ImportDestination): String = when (destination) {
        is ImportDestination.Script -> "Script: ${destination.file.path}\nEnvironment: ${destination.file.parent.path}/kttp.env.json"
        ImportDestination.Drafts -> "Sources → Drafts: new saved GUI requests.\nLinked scripts and environment: ${KttpConsolesPath.getKttpConsolesDir()}"
        is ImportDestination.NewScript -> "New script: ${destination.directory}/${destination.fileName}\nEnvironment: ${destination.directory}/kttp.env.json"
        is ImportDestination.EnvironmentDirectory -> "Environment: ${destination.directory}/kttp.env.json"
    }

    /** Background dialog callers pass the write-safe modality captured before starting their worker. */
    fun commit(
        bundle: ImportBundle,
        selected: Set<Int>,
        destination: ImportDestination,
        baseDirectory: String?,
        modality: ModalityState = ModalityState.nonModal()
    ): ImportCommitResult {
        validate(bundle, selected)
        require(!project.isDisposed) { "The project is closed" }
        require(destination !is ImportDestination.EnvironmentDirectory || selected.isEmpty()) { "Environment-only destination cannot contain requests" }
        if (destination is ImportDestination.NewScript) {
            require(java.nio.file.Path.of(destination.directory).isAbsolute) { "Choose an absolute destination directory" }
        }
        val rendered = renderSelection(bundle, selected, destination, baseDirectory)
        val environment = prepareEnvironment(bundle, destination)
        var result: Result<ImportCommitResult>? = null
        val operation = Runnable { result = runCatching { commitOnEdt(bundle, rendered, destination, environment) } }
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) operation.run() else app.invokeAndWait(operation, modality)
        return requireNotNull(result).getOrThrow()
    }

    private fun renderSelection(
        bundle: ImportBundle, selected: Set<Int>, destination: ImportDestination, baseDirectory: String?
    ): List<Pair<ImportedRequest, String>> {
        val generated = StringBuilder()
        val original = (destination as? ImportDestination.Script)?.originalText.orEmpty()
        return selected.sorted().map { index ->
            val request = bundle.requests[index]
            val scope =
                if (destination is ImportDestination.Script || destination is ImportDestination.NewScript) original + generated else ""
            val source = ImportScriptRenderer.render(request, scope, baseDirectory)
            generated.append(source).append('\n')
            request to source
        }
    }

    private fun commitOnEdt(
        bundle: ImportBundle,
        rendered: List<Pair<ImportedRequest, String>>,
        destination: ImportDestination,
        preparedEnvironment: PreparedEnvironment
    ): ImportCommitResult {
        require(!project.isDisposed) { "The project is closed" }
        val directoryPath = directoryPath(destination)
        return ImportTargetWriter(project).write {
            val directory = VfsUtil.createDirectoryIfMissing(directoryPath) ?: error("Cannot create import directory")
            val existingEnv = directory.findChild("kttp.env.json")?.let {
                FileDocumentManager.getInstance().getDocument(it)?.text ?: error("Cannot read environment file")
            }
            require(existingEnv == preparedEnvironment.original) { "Environment file changed after preparation. Refresh the preview." }
            val environment = preparedEnvironment.text
            val profiles = preparedEnvironment.profiles
            val files = mutableListOf<VirtualFile>()
            val drafts = mutableListOf<GuiDraft>()
            if (environment != null) environment(directory, environment)
            when (destination) {
                is ImportDestination.Script -> {
                    require(!destination.editor.isDisposed && destination.file.isValid && destination.file.isWritable) { "Target editor is unavailable" }
                    require(destination.editor.document.text == destination.originalText) { "Target script changed after preview" }
                    if (rendered.isNotEmpty()) {
                        val generated = rendered.joinToString("\n") { it.second }
                        val offset = insertionOffset(destination, generated)
                        val original = destination.originalText
                        replace(
                            destination.editor.document,
                            original.substring(0, offset) + "\n\n" + generated + "\n" + original.substring(offset)
                        )
                        files += destination.file
                    }
                }

                ImportDestination.Drafts -> rendered.forEach { (request, source) ->
                    val editable = ImportRequestAdapter.adapt(request)
                    if (editable != null) drafts += editable
                    else {
                        val file = create(directory, ".kttp.kts", source)
                        files += file
                        drafts += linkedDraft(request, file)
                    }
                }

                is ImportDestination.NewScript -> if (rendered.isNotEmpty()) {
                    val source = rendered.joinToString("\n") { (request, script) ->
                        "// " + request.sourcePath.replace(Regex("[\\r\\n\\p{Cntrl}]"), " ") + "\n" + script
                    }
                    files += createScript(directory, destination.fileName, source)
                }

                is ImportDestination.EnvironmentDirectory -> Unit
            }
            val retained = (bundle.diagnostics + rendered.flatMap { it.first.diagnostics }).distinct()
                .filter { it.retainedSource != null }
            if (retained.isNotEmpty()) files += create(
                directory,
                ".import-report.txt",
                retained.joinToString("\n\n") { "${it.location}\n${it.message}\n${it.retainedSource}" })
            save()
            val persisted = if (drafts.isEmpty()) emptyList() else project.getService(GuiDraftService::class.java)
                .saveImportedDrafts(drafts)
            ImportCommitResult(persisted, files, profiles)
        }
    }

    private fun directoryPath(destination: ImportDestination): String = when (destination) {
        is ImportDestination.Script -> destination.file.parent.path
        is ImportDestination.NewScript -> destination.directory
        ImportDestination.Drafts -> KttpConsolesPath.getKttpConsolesDir().absolutePath
        is ImportDestination.EnvironmentDirectory -> destination.directory
    }

    private fun prepareEnvironment(bundle: ImportBundle, destination: ImportDestination): PreparedEnvironment {
        val previous = ApplicationManager.getApplication().runReadAction(Computable {
            com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(directoryPath(destination))
                ?.findChild("kttp.env.json")?.let {
                    FileDocumentManager.getInstance().getDocument(it)?.text ?: error("Cannot read environment file")
                }
        })
        val (text, profiles) = ImportEnvironmentWriter.merge(previous, bundle)
        return PreparedEnvironment(previous, text, profiles)
    }

    /**
     * Guards the environment snapshot prepared by the dialog worker before the short write command.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/14
     */
    private data class PreparedEnvironment(val original: String?, val text: String?, val profiles: List<String>)

    /** Reused script properties must be initialized before the imported request uses them. */
    private fun insertionOffset(destination: ImportDestination.Script, generated: String): Int {
        val document = destination.editor.document
        PsiDocumentManager.getInstance(project).commitDocument(document)
        return ApplicationManager.getApplication().runReadAction(Computable {
            val file = PsiManager.getInstance(project).findFile(destination.file) as? KtFile
            val offset = destination.editor.caretModel.offset
            val statements = file?.script?.blockExpression?.statements.orEmpty()
            val caretBoundary =
                statements.firstOrNull { offset in it.textRange.startOffset..it.textRange.endOffset }?.textRange?.endOffset
                    ?: statements.firstOrNull { it.textRange.startOffset >= offset }?.textRange?.startOffset
                    ?: document.textLength
            val names = referencedNames(generated)
            val bindingBoundary = statements.filterIsInstance<KtProperty>()
                .filter { it.name in names }.maxOfOrNull { it.textRange.endOffset } ?: 0
            maxOf(caretBoundary, bindingBoundary)
        })
    }

    private fun referencedNames(source: String): Set<String> = buildSet {
        val lexer = KotlinLexer().apply { start(source) }
        while (lexer.tokenType != null) {
            if (lexer.tokenType == KtTokens.IDENTIFIER) add(lexer.tokenText.removeSurrounding("`"))
            lexer.advance()
        }
    }

    private fun linkedDraft(request: ImportedRequest, file: VirtualFile): GuiDraft = GuiDraft(
        UUID.randomUUID().toString(),
        request.name,
        DraftOwnership.SCRIPT_IMPORTED,
        ImportRequestAdapter.specification(request).let {
            it.copy(identity = it.identity.copy(sourceFile = file.path, declarationIndex = 0))
        },
        bridge = ScriptBridgeState(
            sourceScriptPath = file.path, mode = GuiMode.SCRIPT_ONLY, gaps = listOf(
                SemanticGap(
                    GapKind.SCRIPT_ONLY_FEATURE,
                    message = "Imported request is preserved by its script. Use Follow Script."
                )
            )
        ),
        executionMode = RequestExecutionMode.FOLLOW_SCRIPT
    )

    private fun validate(bundle: ImportBundle, selected: Set<Int>) {
        require(bundle.diagnostics.none { it.severity == ImportSeverity.ERROR }) { "Resolve import errors first" }
        require(selected.isNotEmpty() || bundle.environments.isNotEmpty()) { "Select a request or environment" }
        require(selected.all { it in bundle.requests.indices && bundle.requests[it].selectable }) { "Selection contains an invalid request" }
        selected.forEach { index ->
            val request = bundle.requests[index]
            require(
                !(request.method in setOf(
                    "GET",
                    "HEAD"
                ) && request.body != ImportBody.None)
            ) { "Kttp cannot send a body with GET or HEAD; edit the source explicitly" }
        }
    }
}
