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

package io.github.photowey.kttp.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import io.github.photowey.kttp.cli.execution.EvaluatorOptions
import io.github.photowey.kttp.cli.execution.ExecutionMode
import io.github.photowey.kttp.cli.execution.KttpScript
import io.github.photowey.kttp.cli.execution.LastResponseHolder
import io.github.photowey.kttp.cli.execution.ResponseSnapshot
import io.github.photowey.kttp.cli.model.CliExecutionResult
import io.github.photowey.kttp.core.context.ClientContextImpl
import io.github.photowey.kttp.core.context.CookiesContextImpl
import io.github.photowey.kttp.core.context.KttpContext
import io.github.photowey.kttp.core.context.NoopCookiesContext
import io.github.photowey.kttp.core.context.createKttpContext
import io.github.photowey.kttp.core.context.execution.DeclarationCoordinates
import io.github.photowey.kttp.core.context.execution.ExecutionScenario
import io.github.photowey.kttp.core.context.storage.InMemoryStorage
import io.github.photowey.kttp.core.context.storage.defaultStorage
import io.github.photowey.kttp.core.context.store.EnvironmentStore
import io.github.photowey.kttp.core.context.store.LenientEnvironmentStore
import io.github.photowey.kttp.core.context.store.StrictEnvironmentStore
import io.github.photowey.kttp.core.interceptor.KttpInterceptor
import io.github.photowey.kttp.core.printer.NoopPrinter
import io.github.photowey.kttp.core.printer.Printer
import io.github.photowey.kttp.core.printer.SystemOutPrinter
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.script.experimental.host.StringScriptSource
import kotlin.system.exitProcess

/**
 * Parses CLI options and owns the context lifecycle for compilation, script execution, and cURL output.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/17
 */
class KttpCommand : CliktCommand("kttp") {

    val script by option(help = "Script file path")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)
        .required()

    val requestNumber by option(
        help = "Number of request to be executed. " +
            "Requests are counted starting from 0. " +
            "If omitted, all requests will be executed sequentially."
    ).int()

    val requestName by option(help = "Name of the request to be executed")

    val requestLines by option(help = "Inclusive source lines of one request (first:last, for IDE integration)")
        .convert { value ->
            val lines = value.split(':')
            require(lines.size == 2) { "Expected first:last source lines" }
            DeclarationCoordinates.SourceLines(lines[0].toInt(), lines[1].toInt())
        }

    val envFile by option(help = "Environment file (JSON format)")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)

    val storageDir by option(help = "Storage directory for variables")
        .file(canBeDir = true, canBeFile = false)

    val cookiesFile by option(help = "Cookies file")
        .file(canBeDir = false)

    val envParams by option("--env-param", help = "Environment parameters (key=value)")
        .associate()

    val compilationCache by option(
        names = arrayOf("--compilation-cache", "-c"),
        help = "Use compilation cache"
    ).flag(
        secondaryNames = arrayOf("--no-compilation-cache"),
        default = true
    )

    val debugLog by option(
        names = arrayOf("--debug-log", "-d"),
        help = "Enable debug logging"
    ).flag(default = false)

    val executionMode by option(help = "Execution mode")
        .enum<ExecutionMode>()
        .default(ExecutionMode.DEFAULT)

    val jvmTarget by option(help = "JVM target version for compilation")
        .default("1.8")

    val version by option(
        names = arrayOf("--version", "-v"),
        help = "Show version"
    ).flag(default = false)

    val jsonOutput by option(
        names = arrayOf("--json-output", "-j"),
        help = "Output result as JSON (for IDE integration)"
    ).flag(default = false)

    val responseDir by option(
        help = "Directory to save response files (default: system temp directory)"
    ).file(canBeDir = true, canBeFile = false, mustExist = false)

    val responseManifest by option(
        help = "Write saved response paths as JSON lines to this file (for IDE integration)"
    ).file(canBeDir = false, mustExist = false)

    val responseSnapshot by option(
        help = "Write the latest captured response metadata before script callbacks (for IDE integration)"
    ).file(canBeDir = false, mustExist = false)

    private val snapshot by lazy { responseSnapshot?.let { ResponseSnapshot(it, script.name) } }

    val baseDir by option(
        names = arrayOf("--base-dir", "-B"),
        help = "Base directory for resolving relative paths (e.g. localFile). Default: script file directory. In IDE, use project root so that localFile(\"outer.kttp.json\") resolves to project root."
    ).file(canBeDir = true, canBeFile = false, mustExist = true)

    /**
     * Runs the selected CLI operation in an owned context, writes its result, and exits with code 1 on failure.
     * Response tracking is reset before each invocation and context cleanup runs before output.
     */
    override fun run() {
        if (version) {
            println("Kttp CLI version ${getVersion()}")
            return
        }

        val timingCurl = executionMode == ExecutionMode.CURL
        val t0 = if (timingCurl) System.currentTimeMillis() else 0L

        val responseDirPath = responseDirectory()
        prepareResponseManifest()
        snapshot?.clear()
        LastResponseHolder.clear()

        val result = createContext(responseDirPath).use { context ->
            evaluateScript(context, responseDirPath, t0)
        }

        if (timingCurl) System.err.println("[KTTP_TIMING] cli_total_ms=${System.currentTimeMillis() - t0}")
        outputResult(result)
        if (!result.success) exitProcess(1)
    }

    private fun responseDirectory(): Path = responseDir?.toPath() ?: Paths.get(
        System.getProperty("java.io.tmpdir"),
        ".kttp-responses"
    )

    private fun prepareResponseManifest() {
        responseManifest?.let { manifest ->
            manifest.parentFile?.mkdirs()
            manifest.writeText("")
        }
    }

    private fun createContext(responseDirPath: Path): KttpContext {
        // Empty environments register all requests; unset values fail only when executed.
        val (environmentStore, _) = createEnvironmentStore()
        val storage = storageDir?.let { defaultStorage(it.toPath()) } ?: InMemoryStorage()
        val cookiesContext = cookiesFile?.let { CookiesContextImpl(it.toPath()) } ?: NoopCookiesContext
        val printer = if (jsonOutput) NoopPrinter else SystemOutPrinter
        return createKttpContext(
            storage = storage,
            environmentStore = environmentStore,
            cookiesContext = cookiesContext,
            clientContext = ClientContextImpl(createInterceptor(printer, responseDirPath)),
            printer = printer
        )
    }

    private fun createInterceptor(printer: Printer, responseDirPath: Path) = KttpInterceptor(
        printer = printer,
        responseStorageDir = responseDirPath,
        onRequestCaptured = LastResponseHolder.request::set,
        onResponseCaptured = { response ->
            LastResponseHolder.savedResponseFilePath.set(null)
            LastResponseHolder.response.getAndSet(response)?.close()
            snapshot?.publish()
        },
        onResponseFileSaved = { filePath ->
            LastResponseHolder.savedResponseFilePath.set(filePath)
            responseManifest?.appendText(Json.encodeToString(File(filePath).absolutePath) + "\n")
            snapshot?.publish()
        },
        curlMode = executionMode == ExecutionMode.CURL,
        onResponsePreview = snapshot?.let { checkpoint ->
            { response -> checkpoint.publishPreview(response, LastResponseHolder.request.get()) }
        },
        curlOutput = if (executionMode == ExecutionMode.CURL) {
            { println(it) }
        } else null
    )

    private fun evaluateScript(context: KttpContext, responseDirPath: Path, startedAt: Long): CliExecutionResult {
        val kttpScript = KttpScript(
            context = context,
            sourceCode = StringScriptSource(script.readText(), script.absolutePath),
            scriptName = script.name,
            responseDir = responseDirPath
        )
        val options = evaluatorOptions(responseDirPath)
        if (executionMode == ExecutionMode.CURL) {
            System.err.println("[KTTP_TIMING] cli_ready_ms=${System.currentTimeMillis() - startedAt}")
        }
        return kttpScript.run(options)
    }

    private fun evaluatorOptions(responseDirPath: Path) = EvaluatorOptions(
        executionScenario = buildExecutionScenario(),
        debugLog = debugLog,
        compilationCache = compilationCache,
        executionMode = executionMode,
        jvmTarget = jvmTarget,
        responseDir = responseDirPath,
        baseDir = baseDir?.toPath()
    )

    private fun outputResult(result: CliExecutionResult) {
        if (jsonOutput) {
            println(result.toJson())
        } else if (!result.success) {
            // Successful HTTP responses are already printed by the interceptor.
            System.err.println("Execution failed: ${result.message}")
        }
    }

    /**
     * Loads a flat JSON environment file and overlays command-line values.
     * An empty result enables lenient registration; unreadable environment files produce a warning.
     */
    private fun createEnvironmentStore(): Pair<EnvironmentStore, Boolean> {
        val envMap = mutableMapOf<String, String>()

        // Load from env file if provided
        envFile?.let { file ->
            try {
                val json = file.readText()
                val map = Json.decodeFromString<Map<String, String>>(json)
                envMap.putAll(map)
            } catch (e: Exception) {
                System.err.println("Warning: Failed to load env file: ${e.message}")
            }
        }

        // Override with command line params
        envMap.putAll(envParams)

        val envWasEmpty = envMap.isEmpty()
        val store = if (envWasEmpty) {
            LenientEnvironmentStore(StrictEnvironmentStore(envMap))
        } else {
            StrictEnvironmentStore(envMap)
        }
        return store to envWasEmpty
    }

    /** Selects source lines first, then a nonnegative zero-based number, then a name; otherwise executes the file. */
    private fun buildExecutionScenario(): ExecutionScenario {
        requestLines?.let { return ExecutionScenario.SingleExecution(it) }
        requestNumber?.let { number ->
            // CLI request numbers are zero-based registration indices.
            // IDEA may select by source lines instead; that selector takes precedence above.
            if (number >= 0) {
                return ExecutionScenario.SingleExecution(
                    DeclarationCoordinates(number)
                )
            }
        }

        requestName?.let { name ->
            return ExecutionScenario.SingleExecution(
                DeclarationCoordinates(name)
            )
        }

        return ExecutionScenario.File
    }

    private fun getVersion(): String {
        return KttpCommand::class.java.`package`?.implementationVersion ?: "dev"
    }
}

/**
 * Main entry point for Kttp CLI.
 */
fun main(args: Array<String>) = KttpCommand().main(args)
