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

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import io.github.photowey.kttp.idea.path.KttpPluginPath
import java.io.File

/**
 * Locates script dependency JARs in the installed plugin's kttp-lib directory.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/17
 */
@Service(Service.Level.APP)
class KttpArtifactsProviderImpl : KttpArtifactsProvider {

    private val log = Logger.getInstance(KttpArtifactsProviderImpl::class.java)
    private val jarList: List<File> by lazy { computeRunnerJars() }

    override fun getRunnerJars(): List<File> = jarList

    private fun computeRunnerJars(): List<File> {
        val pluginDir = KttpPluginPath.installedRoot()
        if (pluginDir == null) {
            log.error("Cannot find kttp plugin directory!")
            return emptyList()
        }

        val kttpLibDir = File(pluginDir, "kttp-lib")
        if (!kttpLibDir.isDirectory) {
            log.error("kttp-lib directory not found at: $kttpLibDir")
            return emptyList()
        }
        // Refuse to load from lib/ (would mix plugin classpath with script deps and cause wrong/conflicting jars)
        if (pluginDir.name == "lib") {
            log.error("Plugin dir resolved to 'lib' at $pluginDir; must use plugin root (e.g. kttp-idea). Refusing to list jars.")
            return emptyList()
        }
        if (pluginDir.name != "kttp-idea") {
            log.warn("Plugin dir name is '${pluginDir.name}' at $pluginDir; expected 'kttp-idea'. Using kttp-lib at $kttpLibDir anyway.")
        }

        val jars = kttpLibDir.listFiles { _, name -> name.endsWith(".jar") }?.toList() ?: emptyList()
        log.info("Found ${jars.size} jars in kttp-lib: $kttpLibDir")

        // Log first few jars for debugging
        jars.take(5).forEach { log.info("  - ${it.name}") }

        return jars
    }

}
