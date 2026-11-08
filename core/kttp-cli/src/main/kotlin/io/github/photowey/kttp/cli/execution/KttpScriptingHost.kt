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

import io.github.photowey.kttp.core.context.script.ScriptContext
import io.github.photowey.kttp.dsl.builder.KttpBuilder
import io.github.photowey.kttp.script.Kttp
import java.io.File
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator
import kotlin.script.experimental.jvm.compilationCache
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.CompiledScriptJarsCache
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

/**
 * Compiles and optionally evaluates .kttp.kts scripts in the current JVM.
 * Compiler isolation from IDEA is provided by launching the CLI in a separate JVM,
 * not by this host itself.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/17
 */
class KttpScriptingHost(
    private val useCompilationCache: Boolean,
    private val compileOnly: Boolean,
    private val jvmTarget: String = "1.8"
) {

    /**
     * Evaluates a Kttp script with the given builder context.
     *
     * @param kttpBuilder The builder to provide to the script
     * @param scriptSourceCode The script source code
     * @param baseDir Optional base for localFile resolution, usually the IDE project root.
     * When absent, use the script file's directory when it can be derived from the source location.
     * @return Result of script evaluation with diagnostics
     */
    fun evalScript(
        kttpBuilder: KttpBuilder,
        scriptSourceCode: SourceCode,
        baseDir: Path? = null
    ): ResultWithDiagnostics<EvaluationResult> {
        // Base for relative paths: explicit baseDir (e.g. project root) or script file directory
        val dirForRelativePaths = baseDir?.toAbsolutePath()?.toString()
            ?: extractScriptDirectory(scriptSourceCode.locationId)
        dirForRelativePaths?.let { ScriptContext.setDirectory(it) }

        try {
            val scriptingHost = createScriptingHost()
            return scriptingHost.eval(
                scriptSourceCode,
                createJvmCompilationConfigurationFromTemplate<Kttp> {
                    compilerOptions(buildCompilerOptions())
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
            if (parent.isNotEmpty()) parent else null
        }
    }

    private fun buildCompilerOptions() = buildList {
        addAll(
            listOf(
                "-jvm-target=$jvmTarget",
                "-Xadd-modules=ALL-MODULE-PATH",
                // Fix for Kotlin type checker error in fat jar
                "-Xuse-fast-jar-file-system=false"
            )
        )
    }

    private fun createScriptingHost(): BasicJvmScriptingHost {
        val hostConfiguration = if (useCompilationCache) {
            ScriptingHostConfiguration {
                jvm.compilationCache(createCompiledScriptJarsCache())
            }
        } else {
            null
        }

        val evaluator = if (compileOnly) {
            NoopScriptEvaluator()
        } else {
            BasicJvmScriptEvaluator()
        }

        return BasicJvmScriptingHost(
            baseHostConfiguration = hostConfiguration,
            evaluator = evaluator,
        )
    }

    /** Keys compiled-script JARs by Kttp version, source text/location, JVM target, and non-transient compilation settings. */
    private fun createCompiledScriptJarsCache() =
        CompiledScriptJarsCache { sourceCode, configuration ->
            val cacheKeys = sequenceOf(
                getKttpVersion(),
                sourceCode.text,
                sourceCode.locationId,
                jvmTarget,
                configuration.notTransientData
            )
            val cacheName = cacheKeys.joinToString().sha256()
            val cacheDir = File(System.getProperty("java.io.tmpdir"), ".kttp-cache")
            cacheDir.mkdirs()
            File(cacheDir, "$cacheName.jar")
        }

    private fun String.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(this.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun getKttpVersion(): String {
        // Try to get version from properties or default to unknown
        return try {
            KttpScriptingHost::class.java.`package`?.implementationVersion ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }
}

/**
 * Extension property to check if result has error return value.
 */
val ResultWithDiagnostics<EvaluationResult>.returnValueAsError
    get() = valueOrNull()?.returnValue as? ResultValue.Error

/**
 * No-op script evaluator for compile-only mode.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/17
 */
private class NoopScriptEvaluator(
    private val evaluationConfiguration: ScriptEvaluationConfiguration? = null
) : ScriptEvaluator {

    override suspend fun invoke(
        compiledScript: CompiledScript,
        scriptEvaluationConfiguration: ScriptEvaluationConfiguration
    ): ResultWithDiagnostics<EvaluationResult> {
        return ResultWithDiagnostics.Success(
            EvaluationResult(
                ResultValue.NotEvaluated,
                evaluationConfiguration
            )
        )
    }
}
