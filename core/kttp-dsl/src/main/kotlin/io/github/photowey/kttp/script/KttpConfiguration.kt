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

package io.github.photowey.kttp.script

import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.script.experimental.api.*
import kotlin.script.experimental.dependencies.*
import kotlin.script.experimental.dependencies.maven.MavenDependenciesResolver
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

/**
 * Configures the Kttp script template's default imports, dependencies, and compilation environment.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
object KttpConfiguration : ScriptCompilationConfiguration({
    defaultImports(
        DependsOn::class, Random::class
    )

    defaultImports(
        "kotlin.script.experimental.dependencies.DependsOn",
        "kotlin.random.Random",

        // Kttp DSL
        "io.github.photowey.kttp.dsl.*",
        "io.github.photowey.kttp.dsl.builder.*",
        "io.github.photowey.kttp.dsl.extension.*",
        // ThenContext for then block assertions
        "io.github.photowey.kttp.dsl.ThenContext",
        // ResultExecutable and MappedRequestHolder for then block support
        "io.github.photowey.kttp.core.context.execution.result.*",

        // Core extensions
        "io.github.photowey.kttp.core.extension.auth.*",
        "io.github.photowey.kttp.core.extension.jsonpath.*",
        "io.github.photowey.kttp.core.extension.okhttp.*",

        // Script base class
        "io.github.photowey.kttp.script.Kttp",

        // Assertion libraries - default imports (no need to import manually)
        "kotlin.test.*",
        "org.assertj.core.api.Assertions.assertThat",

        // Local file delegate - default import
        "io.github.photowey.kttp.core.delegate.localFile",
    )

    displayName(
        "Kttp"
    )

    jvm {
        dependenciesFromCurrentContext(wholeClasspath = true)
    }

    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }

    refineConfiguration {
        onAnnotations(
            DependsOn::class,
            Repository::class,
            handler = ::configureMavenDepsOnAnnotations
        )
    }
}) {
    @Suppress("unused")
    private fun readResolve(): Any = HttpScriptEvaluationConfiguration
}

/**
 * Defines script evaluation defaults without shared script instances.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
object HttpScriptEvaluationConfiguration : ScriptEvaluationConfiguration({
    scriptsInstancesSharing(false)
    constructorArgs()
}) {
    @Suppress("unused")
    private fun readResolve(): Any = HttpScriptEvaluationConfiguration
}

fun configureMavenDepsOnAnnotations(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
    val annotations = context.collectedData?.get(ScriptCollectedData.collectedAnnotations)?.takeIf { it.isNotEmpty() }
        ?: return context.compilationConfiguration.asSuccess()

    return runBlocking {
        resolver.resolveFromScriptSourceAnnotations(annotations)
    }.onSuccess {
        context.compilationConfiguration.with {
            dependencies.append(JvmDependency(it))
        }.asSuccess()
    }
}

private val resolver = CompoundDependenciesResolver(
    FileSystemDependenciesResolver(),
    MavenDependenciesResolver()
)
