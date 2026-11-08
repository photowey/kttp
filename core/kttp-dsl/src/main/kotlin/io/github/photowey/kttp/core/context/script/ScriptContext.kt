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

package io.github.photowey.kttp.core.context.script

/**
 * Script execution context for managing script-related information during execution.
 * Used by localFile() delegate to resolve relative paths.
 *
 * The script execution framework sets the directory before script evaluation and clears this object
 * when evaluation finishes. Registered HTTP requests may execute after that cleanup.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/18
 */
object ScriptContext {
    /**
     * Base directory for resource resolution: an explicit runner base directory when supplied,
     * otherwise the script file directory. Used by localFile() for relative resource paths.
     */
    var scriptDirectory: String? = null
        private set

    /**
     * Environment variables available to the script.
     */
    var environment: Map<String, String> = emptyMap()
        private set

    /**
     * Sets the base directory for resource resolution.
     * Called by the script execution framework before script evaluation.
     *
     * @param directory Explicit runner base directory or directory containing the script file
     */
    fun setDirectory(directory: String) {
        scriptDirectory = directory
    }

    /**
     * Sets the environment variables.
     * Called by the script execution framework before script evaluation.
     *
     * @param env Map of environment variable names to values
     */
    fun setEnvironment(env: Map<String, String>) {
        environment = env
    }

    /**
     * Clears all context information.
     * Called by the script execution framework after script evaluation completes.
     */
    fun clear() {
        scriptDirectory = null
        environment = emptyMap()
    }
}
