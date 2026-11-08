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

import io.github.photowey.kttp.core.context.KttpContext
import io.github.photowey.kttp.core.context.execution.result.MappedRequestHolder
import io.github.photowey.kttp.core.context.script.ScriptContext
import io.github.photowey.kttp.dsl.builder.KttpBuilder
import io.github.photowey.kttp.script.Kttp
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.host.StringScriptSource
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import okhttp3.Response
import java.io.File

/**
 * Compiles and evaluates a .kttp.kts script with the given [KttpContext].
 * Evaluation populates [KttpContext.executionContext] with the script's request registrations.
 * Script code, including delegated request results, can also execute during evaluation.
 *
 * This in-process evaluator lives in kttp-cli; HTTP execution is implemented in kttp-dsl.
 * IDEA's Follow Script mode instead launches the CLI through its shared process runner.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/18
 */
object KttpScriptEvaluator {

    // Lazy init: scripting host creation can fail in IDE plugin classloader (e.g. missing
    // scripting runtime); defer to first use so "Could not initialize class" is avoided and
    // any real error is thrown at evaluate() with a clear stack trace.
    private val scriptingHost by lazy {
        BasicJvmScriptingHost(evaluator = BasicJvmScriptEvaluator())
    }

    /**
     * Evaluates [scriptSource] (script text or script source). The script receives
     * a [KttpBuilder] as its constructor argument (created from [context]).
     * When evaluation completes without a script error, the script body has run and
     * [context].executionContext.registrations contains the requests it registered.
     *
     * @return Diagnostics and the evaluation result. Compilation and host failures may return
     * [ResultWithDiagnostics.Failure]; a [ResultWithDiagnostics.Success] can still contain
     * [kotlin.script.experimental.api.ResultValue.Error]. Check [returnValueAsError] as well.
     */
    fun evaluate(
        scriptSource: SourceCode,
        context: KttpContext
    ): ResultWithDiagnostics<EvaluationResult> {
        // Set script directory context before evaluation
        val scriptDir = extractScriptDirectory(scriptSource.locationId)
        scriptDir?.let { ScriptContext.setDirectory(it) }

        try {
            val kttpBuilder: KttpBuilder = context.builderFactory.createKttpBuilder()
            return scriptingHost.eval(
                scriptSource,
                createJvmCompilationConfigurationFromTemplate<Kttp> {
                    compilerOptions(listOf("-jvm-target", "1.8"))
                },
                ScriptEvaluationConfiguration {
                    constructorArgs(kttpBuilder)
                }
            )
        } finally {
            // Clear script context after evaluation
            ScriptContext.clear()
        }
    }

    /**
     * Extracts script directory from location ID (script file path).
     */
    private fun extractScriptDirectory(locationId: String?): String? {
        if (locationId == null) return null
        val scriptFile = File(locationId)
        return if (scriptFile.exists() && scriptFile.isFile) {
            scriptFile.parent
        } else {
            // If locationId is not a file path, try to extract directory from it
            val parent = File(locationId).parent
            if (parent != null && parent.isNotEmpty()) parent else null
        }
    }

    /**
     * Convenience: evaluate script from string (e.g. file content).
     * Uses [locationId] as script location identifier (e.g. file path or "script.kttp.kts").
     */
    fun evaluate(
        scriptText: String,
        locationId: String,
        context: KttpContext
    ): ResultWithDiagnostics<EvaluationResult> =
        evaluate(StringScriptSource(scriptText, locationId), context)

    /**
     * Runs the first registration (index 0) unless it is ignored, without searching
     * for another runnable registration. Call after [evaluate] succeeds and its
     * evaluation result contains no script error. Execution may also invoke response
     * listeners installed by `then` callbacks; these do not replace the registered holder.
     * Returns a [Response] result directly. If a [MappedRequestHolder] is registered and
     * returns a non-null value, its response is taken from [MappedRequestHolder.getLastResponse].
     * Returns null for an absent/ignored registration, a null execution result, or an
     * otherwise unavailable response.
     */
    fun runFirstRequest(context: KttpContext): Response? {
        val result = context.executionContext.runFirstRegistration() ?: return null
        if (result is Response) return result
        val reg = context.executionContext.registrations.firstOrNull() ?: return null
        val executable = reg.executable
        if (executable is MappedRequestHolder<*>) return executable.getLastResponse()
        return null
    }
}
