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

package io.github.photowey.kttp.cli.execution

import io.github.photowey.kttp.cli.model.CliExecutionResult
import io.github.photowey.kttp.core.context.KttpContext
import io.github.photowey.kttp.core.context.execution.DeclarationCoordinates
import io.github.photowey.kttp.core.context.execution.ExecutionScenario
import io.github.photowey.kttp.core.interceptor.CapturedRequest
import io.github.photowey.kttp.core.printer.SystemOutPrinter
import io.github.photowey.kttp.core.printer.debugln
import io.github.photowey.kttp.core.printer.println
import okhttp3.Response
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.jvm.util.isError
import kotlin.time.measureTime

/**
 * Holder for the last captured response across instances.
 * Used to pass response from interceptor back to script runner.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/17
 */
internal object LastResponseHolder {
    val response: AtomicReference<Response?> = AtomicReference(null)
    val request: AtomicReference<CapturedRequest?> = AtomicReference(null)
    val savedResponseFilePath: AtomicReference<String?> = AtomicReference(null)

    fun clear() {
        response.set(null)
        request.set(null)
        savedResponseFilePath.set(null)
    }
}

/**
 * Coordinates script compilation/evaluation and scenario execution, preserving the last captured HTTP response
 * when later execution or response callbacks fail.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/17
 */
class KttpScript(
    private val context: KttpContext,
    private val sourceCode: SourceCode,
    private val scriptName: String,
    private val onRequestCaptured: ((CapturedRequest) -> Unit)? = null,
    private val onResponseCaptured: ((Response) -> Unit)? = null,
    private val responseDir: java.nio.file.Path? = null  // Response directory for saving files
) {
    private val printer = SystemOutPrinter
    private var capturedRequest: CapturedRequest? = null
    private var capturedResponse: Response? = null
    private var executionError: String? = null
    private var executionException: Throwable? = null

    /**
     * Runs the configured script mode, returning elapsed time and the last captured response.
     * A callback failure can yield an unsuccessful result that still contains an HTTP response.
     */
    fun run(options: EvaluatorOptions): CliExecutionResult {
        val executionTime = measureTime {
            doRun(options)
        }

        return buildExecutionResult(executionTime.inWholeMilliseconds)
    }

    private fun doRun(options: EvaluatorOptions): ResultWithDiagnostics<EvaluationResult> {
        val (
            executionScenario,
            debugLog,
            compilationCache,
            executionMode,
            jvmTarget
        ) = options

        val timingCurl = executionMode == ExecutionMode.CURL
        val doRunStart = if (timingCurl) System.currentTimeMillis() else 0L

        if (debugLog) printOptions(options)

        val kttpScriptingHost = KttpScriptingHost(
            compilationCache,
            executionMode == ExecutionMode.COMPILE_ONLY,
            jvmTarget
        )

        val sourceName = File(sourceCode.locationId ?: scriptName).name
        context.executionContext.addRegistrationCustomizer { registration ->
            // The first script frame identifies the declaration, including helper-created registrations.
            Thread.currentThread().stackTrace.firstOrNull { it.fileName == sourceName && it.lineNumber > 0 }
                ?.let { registration.coordinates.add(DeclarationCoordinates.SourceLines(it.lineNumber)) }
            registration
        }
        val kttpBuilder = context.builderFactory.createKttpBuilder()
        val evalStart = if (timingCurl) System.currentTimeMillis() else 0L
        val result = kttpScriptingHost.evalScript(kttpBuilder, sourceCode, options.baseDir)
        if (timingCurl) System.err.println("[KTTP_TIMING] script_eval_ms=${System.currentTimeMillis() - evalStart}")

        if (result.isError()) {
            executionError = extractErrorMessage(result)
            handleErrors(result)
            return result
        }

        val execStart = if (timingCurl) System.currentTimeMillis() else 0L
        when (executionMode) {
            ExecutionMode.COMPILE_ONLY -> {
                println("The script was compiled without evaluation")
            }

            ExecutionMode.DEFAULT -> {
                executeAndCapture(executionScenario)
            }

            ExecutionMode.CURL -> {
                executeAndCapture(executionScenario)
            }
        }
        if (timingCurl) System.err.println("[KTTP_TIMING] script_execute_ms=${System.currentTimeMillis() - execStart}")
        if (timingCurl) System.err.println("[KTTP_TIMING] script_doRun_total_ms=${System.currentTimeMillis() - doRunStart}")

        return result
    }

    private fun executeAndCapture(scenario: ExecutionScenario) {
        try {
            context.executionContext.execute(scenario)
        } catch (e: Throwable) {
            executionException = e
        }
    }

    /** Combines evaluation/execution errors with the last captured response so later failures do not discard HTTP details. */
    private fun buildExecutionResult(executionTimeMs: Long): CliExecutionResult {
        executionError?.let { error ->
            return failedExecution("Script execution failed", error, executionTimeMs)
        }
        executionException?.let { e ->
            val message = e.message ?: e.toString()
            val response = LastResponseHolder.response.get()
            return if (response == null) failedExecution(message, message, executionTimeMs)
            else responseResult(response, executionTimeMs).copy(success = false, message = message, error = message)
        }
        val response = LastResponseHolder.response.get() ?: return CliExecutionResult(
            success = true,
            scriptName = scriptName,
            message = "Script executed (no HTTP response)",
            executionTimeMs = executionTimeMs
        )
        return responseResult(response, executionTimeMs)
    }

    private fun failedExecution(message: String, error: String, executionTimeMs: Long) = CliExecutionResult(
        success = false,
        scriptName = scriptName,
        message = message,
        error = error,
        executionTimeMs = executionTimeMs
    )

    private fun responseResult(response: Response, executionTimeMs: Long): CliExecutionResult {
        val capturedFile = LastResponseHolder.savedResponseFilePath.get()?.let(::File)
        val result = capturedResponseResult(
            scriptName,
            response,
            LastResponseHolder.request.get(),
            capturedFile,
            executionTimeMs
        )
        val responseFile = capturedFile ?: saveResponse(response, result.body)
        return result.copy(responseFilePath = responseFile?.absolutePath)
    }

    private fun saveResponse(response: Response, body: String?): File? {
        return try {
            // Use this runner's response directory, falling back to a subdirectory of system temp.
            val responsesDir = responseDir?.toFile() ?: File(System.getProperty("java.io.tmpdir"), ".kttp-responses")
            responsesDir.mkdirs()

            // Use yyyy-MM-dd'T'HHmmss for response file timestamps.
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss"))
            val filename = "${scriptName}_$timestamp.json"
            val file = File(responsesDir, filename)

            val content = buildString {
                appendLine("HTTP/1.1 ${response.code} ${response.message}")
                response.headers.forEach { (name, value) ->
                    appendLine("$name: $value")
                }
                appendLine()
                body?.let { append(it) }
            }

            file.writeText(content)
            file
        } catch (e: Exception) {
            System.err.println("Failed to save response: ${e.message}")
            null
        }
    }

    private fun handleErrors(result: ResultWithDiagnostics<EvaluationResult>) {
        result.returnValueAsError
            ?.error
            ?.printStackTrace()

        result.reports.forEach { diagnostic ->
            if (diagnostic.severity > ScriptDiagnostic.Severity.DEBUG) {
                val message = diagnostic.render()
                System.err.println(message)
            }
        }
    }

    private fun extractErrorMessage(result: ResultWithDiagnostics<EvaluationResult>): String {
        val errors = mutableListOf<String>()

        result.returnValueAsError?.error?.let {
            errors.add(it.message ?: it.toString())
        }

        result.reports
            .filter { it.severity >= ScriptDiagnostic.Severity.ERROR }
            .forEach { errors.add(it.render()) }

        return if (errors.isEmpty()) "Script execution failed" else errors.joinToString("\n")
    }

    private fun printOptions(options: EvaluatorOptions) {
        printer.debugln("Evaluator options: $options")
    }
}
