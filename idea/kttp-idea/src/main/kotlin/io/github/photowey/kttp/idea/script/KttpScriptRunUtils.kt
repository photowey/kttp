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

import io.github.photowey.kttp.idea.cli.KttpCliLocator
import java.io.File

/**
 * Utility for Kttp script execution.
 * Used by [KttpScriptAdditionalIdeaDependenciesProvider] and [KttpScriptDefinitionsProvider]
 * to get script classpath / External Libraries from plugin's kttp-lib directory only.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/17
 */
object KttpScriptRunUtils {

    /** Returns jar files from plugin kttp-lib (via [KttpArtifactsProvider.getRunnerJars]). */
    fun getKttpRunnerJars(): List<File> {
        return KttpArtifactsProvider.getInstance().getRunnerJars()
    }

    /**
     * Finds the Kttp CLI JAR file.
     * Shared with other IDEA-side launchers via [KttpCliLocator].
     */
    fun getKttpCliJar(): File? {
        return KttpCliLocator.findCliJar()
    }
}
