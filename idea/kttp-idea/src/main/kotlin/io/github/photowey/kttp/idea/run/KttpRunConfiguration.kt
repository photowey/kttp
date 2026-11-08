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

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.jar.JarApplicationConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import kotlin.text.isBlank

/**
 * Stores Kttp script launch settings as an IDEA JAR application configuration.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String?
) : JarApplicationConfiguration(project, factory, name),
    WithoutOwnBeforeRunSteps,
    SearchScopeProvidingRunProfile {

    var scriptPath: String?
        get() = getOptions().scriptPath
        set(value) {
            getOptions().scriptPath = value
        }

    var runStrategy: ScriptRunStrategy
        get() = getOptions().getRunStrategy()
        set(value) {
            getOptions().setRunStrategy(value)
        }

    var environmentVariables: Map<String, String>
        get() = getOptions().environmentVariables
        set(value) {
            getOptions().environmentVariables = value
        }

    var selectedEnvironment: String?
        get() = getOptions().selectedEnvironment
        set(value) {
            getOptions().selectedEnvironment = value
        }

    override fun getState(
        executor: Executor,
        environment: ExecutionEnvironment
    ): RunProfileState {
        return KttpRunProfileState(this, environment)
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return KttpRunConfigurationEditor()
    }

    override fun checkConfiguration() {
        val path = scriptPath
        if (path == null || path.isBlank()) {
            throw RuntimeConfigurationError("Script file must be specified")
        }
        if (!FileUtil.exists(path)) {
            throw RuntimeConfigurationError("Script file not found: $path")
        }
        checkSettings(runStrategy)
    }

    override fun getOptions(): KttpRunConfigurationOptions {
        return super.getOptions() as KttpRunConfigurationOptions
    }

    override fun getOptionsClass(): Class<out RunConfigurationOptions> {
        return KttpRunConfigurationOptions::class.java
    }

    override fun getSearchScope(): GlobalSearchScope? {
        val vf = getScriptVirtualFile() ?: return null
        val scriptFileSearchScope = GlobalSearchScope.FilesScope.fileScope(project, vf)
        return GlobalSearchScope.allScope(project).uniteWith(scriptFileSearchScope)
    }

    override fun suggestedName(): String? {
        val path = getScriptFileNioPath() ?: return null
        val fileName = path.fileName.toString()
        // Remove .kttp.kts extension
        val nameWithoutExt = fileName.substringBeforeLast(".")
            .substringBeforeLast(".")
        return nameWithoutExt.takeIf { it.isNotEmpty() }
    }
}
