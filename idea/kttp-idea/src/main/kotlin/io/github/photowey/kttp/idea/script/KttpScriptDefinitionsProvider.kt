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

package io.github.photowey.kttp.idea.script

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.io.File
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath

/**
 * Provides Kttp script definition to the Kotlin plugin.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpScriptDefinitionsProvider(private val project: Project) : kotlin.script.experimental.intellij.ScriptDefinitionsProvider {

    override val id: String = "kttp"

    override fun getDefinitionClasses(): Iterable<String> {
        // Return empty in unit test mode
        if (ApplicationManager.getApplication().isUnitTestMode) {
            return emptyList()
        }
        return listOf("io.github.photowey.kttp.script.Kttp")
    }

    override fun getDefinitionsClassPath(): Iterable<File> {
        // Return empty in unit test mode
        if (ApplicationManager.getApplication().isUnitTestMode) {
            return emptyList()
        }
        return KttpScriptRunUtils.getKttpRunnerJars()
    }

    /** Supplies editor dependencies through the script configuration on both old and new Kotlin plugins. */
    override fun provideDefinitions(
        baseHostConfiguration: ScriptingHostConfiguration,
        loadedScriptDefinitions: List<ScriptDefinition>
    ): Iterable<ScriptDefinition> {
        if (loadedScriptDefinitions.isEmpty()) return emptyList()
        val jars = KttpScriptRunUtils.getKttpRunnerJars()
        KttpScriptLibrary().ensureInstalledLibrary(project)
        return loadedScriptDefinitions.map { definition ->
            ScriptDefinition(
                definition.compilationConfiguration.with { jvm { updateClasspath(jars) } },
                definition.evaluationConfiguration
            )
        }
    }

    override fun useDiscovery(): Boolean = false
}
