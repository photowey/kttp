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

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import io.github.photowey.kttp.core.context.execution.DeclarationCoordinates
import io.github.photowey.kttp.idea.cli.KttpCliInvocation
import io.github.photowey.kttp.idea.env.KttpEnvironmentService
import io.github.photowey.kttp.idea.execution.RequestExecutionMode
import io.github.photowey.kttp.idea.io.HistoryPersistence
import io.github.photowey.kttp.idea.path.KttpConsolesPath
import io.github.photowey.kttp.idea.script.sourceLines
import io.github.photowey.kttp.idea.script.toKttpScript
import io.github.photowey.kttp.idea.toolwindow.KttpToolWindowPanel
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Launches the Kttp CLI and imports saved responses into IDEA history.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpRunProfileState(
    runConfiguration: KttpRunConfiguration,
    environment: ExecutionEnvironment
) : com.intellij.execution.application.BaseJavaApplicationCommandLineState<KttpRunConfiguration>(
    environment,
    runConfiguration
) {
    private val runConfiguration: KttpRunConfiguration = runConfiguration
    private val responseManifest by lazy { Files.createTempFile("kttp-run-responses-", ".jsonl").toFile() }

    override fun createJavaParameters(): JavaParameters {
        val scriptPath: Path = runConfiguration.getScriptFileNioPath()
            ?: throw CantRunException("Script file is not set")

        val scriptVf = runConfiguration.getScriptVirtualFile()
        val envVars = KttpEnvironmentService.getInstance(runConfiguration.project)
            .getEnvironmentVariables(scriptVf)
        val responsesDir = KttpConsolesPath.getResponsesDir()

        val invocation = KttpCliInvocation(
            scriptPath = scriptPath.toAbsolutePath().toString(),
            requestName = (runConfiguration.runStrategy as? ScriptRunStrategy.SingleDeclaration)
                ?.coordinates?.let { it as? DeclarationCoordinates.Name }?.name,
            requestLines = ApplicationManager.getApplication().runReadAction(Computable<String?> {
                val coordinates = (runConfiguration.runStrategy as? ScriptRunStrategy.SingleDeclaration)?.coordinates
                if (coordinates !is DeclarationCoordinates.Number) null
                else {
                    val script = scriptVf?.toKttpScript(runConfiguration.project)
                        ?: throw CantRunException("Cannot read the selected script request")
                    val declaration = script.getRequestDeclaration(coordinates)
                        ?: throw CantRunException("The selected request is no longer available")
                    declaration.sourceLines().asString()
                }
            }),
            envVariables = envVars,
            responseDir = responsesDir.absolutePath,
            responseManifest = responseManifest.absolutePath,
            baseDir = runConfiguration.project.basePath
        )

        return try {
            kttpRunScriptJavaParameters(project = runConfiguration.project, invocation = invocation)
        } catch (failure: Throwable) {
            responseManifest.delete()
            throw failure
        }
    }

    override fun startProcess(): com.intellij.execution.process.OSProcessHandler {
        val responsesDir = KttpConsolesPath.getResponsesDir()
        val scriptPath = runConfiguration.scriptPath
        val scriptName = runConfiguration.suggestedName() ?: "kttp"
        val handler = try {
            super.startProcess()
        } catch (failure: Throwable) {
            responseManifest.delete()
            throw failure
        }
        handler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                val newResponseFiles = try {
                    KttpRunResponseManifest.read(responseManifest, responsesDir)
                } finally {
                    responseManifest.delete()
                }
                // Refresh Scratches and Consoles VFS so new response files appear in the tree
                ApplicationManager.getApplication().invokeLater {
                    if (newResponseFiles.isNotEmpty()) {
                        try {
                            HistoryPersistence.append(
                                appendResponseHistory(emptyList(), newResponseFiles, scriptName, scriptPath)
                            )
                        } catch (failure: IOException) {
                            Messages.showErrorDialog(
                                runConfiguration.project,
                                failure.message,
                                "Cannot Save Kttp History"
                            )
                        }
                    }

                    val kttpDir = KttpConsolesPath.getKttpConsolesDir()
                    val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(kttpDir)
                    if (vf != null) {
                        VfsUtil.markDirtyAndRefresh(false, true, true, vf)
                    }
                    // 显式刷新侧边栏 Responses 树，不依赖 VFS 事件
                    KttpToolWindowPanel.refreshResponseTreeIfVisible(runConfiguration.project)
                }
            }
        })
        return handler
    }

    /**
     * Appends completed script response files to shared execution history.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    companion object {
        internal fun appendResponseHistory(
            existing: List<HistoryPersistence.Record>,
            responseFiles: List<java.io.File>,
            scriptName: String,
            scriptPath: String?
        ): List<HistoryPersistence.Record> {
            val records = existing.toMutableList()
            responseFiles.forEach { file ->
                records.add(
                    HistoryPersistence.Record(
                        scriptName,
                        file.absolutePath,
                        scriptPath,
                        executionMode = RequestExecutionMode.FOLLOW_SCRIPT
                    )
                )
            }
            return records.distinctBy { it.responseFilePath }
        }
    }
}
