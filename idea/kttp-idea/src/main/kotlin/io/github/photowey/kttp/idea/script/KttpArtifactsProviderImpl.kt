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

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

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
        val pluginDir = getPluginDir()
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

    private fun getPluginDir(): File? {
        return try {
            // Method 1: Use PluginManagerCore to get plugin path
            val pluginId = PluginId.getId("io.github.photowey.kttp")
            val plugin = PluginManagerCore.getPlugin(pluginId)

            if (plugin != null) {
                val pluginPath = plugin.pluginPath.toFile()
                log.info("Plugin path from PluginManagerCore: $pluginPath")

                // Normalize: if pluginPath is a file (jar), get plugin root directory
                val pluginRoot = when {
                    pluginPath.isDirectory -> pluginPath
                    pluginPath.isFile -> {
                        // pluginPath points to lib/kttp-idea.jar, need parent (lib) -> parent (kttp-idea)
                        pluginPath.parentFile?.parentFile
                    }

                    else -> null
                }

                if (pluginRoot != null && pluginRoot.isDirectory) {
                    log.info("Resolved plugin root directory: $pluginRoot")
                    return pluginRoot
                } else {
                    log.warn("Could not resolve plugin root from: $pluginPath, falling back to class location")
                    findFromClassLocation()
                }
            } else {
                // Method 2: Fallback to class location
                findFromClassLocation()
            }
        } catch (e: Exception) {
            log.error("Error getting plugin directory", e)
            null
        }
    }

    private fun findFromClassLocation(): File? {
        return try {
            val clazz = KttpArtifactsProviderImpl::class.java
            val codeSource = clazz.protectionDomain?.codeSource?.location
                ?: return null

            val jarFile = when (codeSource.protocol) {
                "file" -> {
                    try {
                        val path = URLDecoder.decode(codeSource.path, StandardCharsets.UTF_8.name())
                        File(path)
                    } catch (e: Exception) {
                        log.warn("Failed to decode file path, using original: ${codeSource.path}", e)
                        File(codeSource.path)
                    }
                }

                "jar" -> {
                    try {
                        val p = codeSource.path
                        val sep = p.indexOf("!")
                        if (sep >= 0) {
                            val jarPathPart = p.substring(0, sep)
                            // Handle "file:/path" or "file:///path"
                            val path = if (jarPathPart.startsWith("file:")) {
                                URLDecoder.decode(jarPathPart.substring(5), StandardCharsets.UTF_8.name())
                            } else {
                                URLDecoder.decode(jarPathPart, StandardCharsets.UTF_8.name())
                            }
                            File(path)
                        } else {
                            File(URLDecoder.decode(p, StandardCharsets.UTF_8.name()))
                        }
                    } catch (e: Exception) {
                        log.warn("Failed to decode jar path, using original: ${codeSource.path}", e)
                        val p = codeSource.path
                        val sep = p.indexOf("!")
                        if (sep >= 0) {
                            val jarPathPart = p.substring(0, sep)
                            // Handle "jar:file:/path" format
                            val path = if (jarPathPart.startsWith("jar:file:")) {
                                jarPathPart.substring(9) // Remove "jar:file:"
                            } else if (jarPathPart.startsWith("file:")) {
                                jarPathPart.substring(5) // Remove "file:"
                            } else {
                                jarPathPart
                            }
                            File(path)
                        } else {
                            File(p)
                        }
                    }
                }

                else -> return null
            }

            log.info("Class location jar: $jarFile")

            // Navigate: lib/kttp-idea.jar -> parent is kttp-idea/
            val libDir = jarFile.parentFile
            val pluginDir = libDir?.parentFile

            log.info("Derived plugin dir from class location: $pluginDir")
            pluginDir
        } catch (e: Exception) {
            log.error("Error finding from class location", e)
            null
        }
    }
}
