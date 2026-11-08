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

package io.github.photowey.kttp.idea.env

import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import io.github.photowey.kttp.idea.path.KttpConsolesPath
import java.io.File

/**
 * Environment variable entry in kttp.env.json.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
data class KttpEnvironment(
    val name: String,
    val variables: Map<String, String>,
    private val jsonPropertyPointer: SmartPsiElementPointer<JsonProperty>?
) {
    fun getNavigatable() = jsonPropertyPointer?.element
}

/**
 * Project-level service for Kttp environment management.
 * Handles kttp.env.json file discovery, parsing, and environment selection.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
@Service(Service.Level.PROJECT)
class KttpEnvironmentService(private val project: Project) {

    var selectedEnvironment: String? = null
        private set

    /**
     * Get all available environments from kttp.env.json.
     */
    fun getAvailableEnvironments(scriptFile: VirtualFile? = null): List<KttpEnvironment> {
        val envFile = findEnvFile(scriptFile) ?: return emptyList()
        return parseEnvironments(envFile)
    }

    /**
     * Set the selected environment by name.
     */
    fun setSelectedEnvironment(name: String?) {
        selectedEnvironment = name
    }

    /**
     * Find kttp.env.json in the script's directory, then in Consoles/Kttp.
     */
    fun findEnvFile(scriptFile: VirtualFile? = null): VirtualFile? {
        // First try script's parent directory
        if (scriptFile != null) {
            val parentDir = scriptFile.parent
            val envFile = parentDir?.findChild(KttpEnvFileType.FILE_NAME)
            if (envFile != null) return envFile
        }

        val consoleEnvFile = File(KttpConsolesPath.getKttpConsolesDir(), KttpEnvFileType.FILE_NAME)
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(consoleEnvFile)
    }

    /**
     * Parse environments from kttp.env.json file.
     */
    private fun parseEnvironments(envFile: VirtualFile): List<KttpEnvironment> {
        val psiFile = PsiManager.getInstance(project).findFile(envFile) as? JsonFile ?: return emptyList()
        val topObject = psiFile.topLevelValue as? JsonObject ?: return emptyList()

        return topObject.propertyList.mapNotNull { property ->
            val name = property.name
            val value = property.value
            if (value is JsonObject) {
                val variables = value.propertyList.associate { prop ->
                    val variable = prop.value
                    // Strings are decoded; other JSON values retain their existing source-text semantics.
                    prop.name to if (variable is JsonStringLiteral) variable.value else variable?.text.orEmpty()
                }
                KttpEnvironment(
                    name = name,
                    variables = variables,
                    jsonPropertyPointer = SmartPointerManager.createPointer(property)
                )
            } else null
        }
    }

    /**
     * Get environment variables for the selected environment.
     */
    fun getEnvironmentVariables(scriptFile: VirtualFile? = null): Map<String, String> {
        val envName = selectedEnvironment ?: return emptyMap()
        val envs = getAvailableEnvironments(scriptFile)
        return envs.find { it.name == envName }?.variables ?: emptyMap()
    }

    /**
     * Resolves the environment service for a project.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    companion object {
        fun getInstance(project: Project): KttpEnvironmentService = project.service()
    }
}
