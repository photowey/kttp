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

package io.github.photowey.kttp.idea.execution

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.Logger
import io.github.photowey.kttp.idea.cli.KttpCliInvocation
import io.github.photowey.kttp.idea.cli.KttpCliJsonResultParser
import io.github.photowey.kttp.idea.gui.GuiRequestCancellation
import io.github.photowey.kttp.idea.run.kttpRunScriptJavaParameters
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Runs GUI Follow Script requests in a separate CLI JVM using the same launcher as script Run.
 * Execution blocks the caller; the composer dispatches it to a worker thread.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal object ScriptRequestExecutor {
    /**
     * Synchronously runs one selected script request using the script Run launcher; call from a worker thread.
     * Cancellation terminates the process. A private checkpoint may recover an already captured response after failure.
     */
    fun execute(
        project: Project,
        invocation: KttpCliInvocation,
        cancellation: GuiRequestCancellation,
        onResponsePreview: ((KttpExecutionResult) -> Unit)? = null
    ): KttpExecutionResult {
        val started = System.nanoTime()
        val title = File(invocation.scriptPath).name
        var checkpointDirectory: File? = null
        var checkpoint: File? = null
        val result = try {
            require(invocation.requestLines != null || invocation.requestNumber != null || invocation.requestName != null) { "Select one script request before sending." }
            if (cancellation.isCancelled) return cancelled(title, started)
            checkpointDirectory = Files.createTempDirectory("kttp-script-response-").toFile()
            checkpoint = checkpointDirectory.resolve("response.json")
            val observer = onResponsePreview?.let {
                runCatching { ScriptResponseObserver(checkpoint, title, it) }.onFailure { failure ->
                    Logger.getInstance(ScriptRequestExecutor::class.java).warn("Live response preview is unavailable", failure)
                }.getOrNull()
            }
            val output = observer.use {
                runScript(project, invocation.copy(responseSnapshot = checkpoint.path), cancellation)
            }
            when {
                output.isCancelled || cancellation.isCancelled -> KttpExecutionResult(title, false, "Request cancelled")
                output.isTimeout -> KttpExecutionResult(title, false, "Script execution timed out")
                else -> {
                    val parsed = KttpCliJsonResultParser.parse(title, output.stdout)
                    if (output.exitCode != 0 && parsed.success) parsed.copy(
                        success = false,
                        message = output.stderr.trim().ifBlank { "CLI exited with code ${output.exitCode}" })
                    else parsed
                }
            }
        } catch (failure: Exception) {
            KttpExecutionResult(
                title,
                false,
                if (cancellation.isCancelled) "Request cancelled" else failure.message ?: "Script execution failed"
            )
        }
        return try {
            recoverScriptResponse(checkpoint, invocation, result).copy(
                executionMode = RequestExecutionMode.FOLLOW_SCRIPT,
                totalTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            )
        } finally {
            checkpointDirectory?.deleteRecursively()
        }
    }

    private fun runScript(
        project: Project,
        invocation: KttpCliInvocation,
        cancellation: GuiRequestCancellation
    ): com.intellij.execution.process.ProcessOutput {
        val indicator = EmptyProgressIndicator()
        return cancellation.attachAction(indicator::cancel).use {
            val command = kttpRunScriptJavaParameters(project, invocation.copy(jsonOutput = true)).toCommandLine()
            if (cancellation.isCancelled) com.intellij.execution.process.ProcessOutput().apply { setCancelled() }
            // Like script Run, wait for completion until the user cancels; HTTP timeouts remain script-controlled.
            else CapturingProcessHandler(command).runProcessWithProgressIndicator(indicator)
        }
    }

    private fun cancelled(title: String, started: Long) = KttpExecutionResult(
        title,
        false,
        "Request cancelled",
        executionMode = RequestExecutionMode.FOLLOW_SCRIPT,
        totalTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
    )
}
