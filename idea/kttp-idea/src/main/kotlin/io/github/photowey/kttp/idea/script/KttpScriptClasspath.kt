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

import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import io.github.photowey.kttp.script.KttpConfiguration
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Standalone fallback classpath lookup, currently unused by the registered script providers.
 * [KttpScriptDefinitionsProvider] and [KttpScriptAdditionalIdeaDependenciesProvider] obtain
 * their runtime jars directly through [KttpScriptRunUtils].
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
internal object KttpScriptClasspath {

    private const val KTPP_PLUGIN_ID = "io.github.photowey.kttp"

    private val libraryNames = setOf(
        "kttp",
        "kotlin-stdlib",
        "kotlin-reflect",
        "kotlin-script",
        "okhttp",
        "jackson",
        "kotlinx-serialization",
        "kotlinx-coroutines",
        "json-path",
        "ok2curl",
    )

    /**
     * Returns the first nonempty candidate set: packaged kttp-lib, classloader paths,
     * matching jars beside this class's code source, then kttp-lib from plugin metadata.
     * The classloader and sibling-jar fallbacks filter candidates using [libraryNames].
     */
    fun getClasspathFiles(): List<File> {
        val fromKttpLib = try {
            KttpArtifactsProvider.getInstance().getRunnerJars()
        } catch (_: Throwable) {
            emptyList()
        }
        if (fromKttpLib.isNotEmpty()) return fromKttpLib
        val fromLoader = fromClassloader(KttpConfiguration::class.java.classLoader)
        if (fromLoader.isNotEmpty()) return fromLoader
        val fromPluginLib = fromPluginLibDirectory()
        if (fromPluginLib.isNotEmpty()) return fromPluginLib
        val fromPluginPath = fromPluginPathByPluginManager()
        if (fromPluginPath.isNotEmpty()) return fromPluginPath
        return emptyList()
    }

    private fun fromClassloader(loader: ClassLoader): List<File> {
        val paths = classpathFromClassloader(loader) ?: return emptyList()
        return paths
            .filter { path -> libraryNames.any { lib -> lib in path } }
            .map { File(it) }
            .filter { it.exists() }
            .distinct()
    }

    private fun classpathFromClassloader(loader: ClassLoader): List<String>? {
        val urls: List<URL> = when (loader) {
            is URLClassLoader -> loader.urLs.toList()
            else -> {
                val parent = loader.parent ?: return classpathFromCodeSource()
                return classpathFromClassloader(parent)
            }
        }
        return try {
            urls.mapNotNull { url ->
                when (url.protocol) {
                    "file" -> URLDecoder.decode(url.path, StandardCharsets.UTF_8.name())
                    "jar" -> {
                        val path = url.path
                        val sep = path.indexOf("!")
                        if (sep >= 0) URLDecoder.decode(path.substring(0, sep), StandardCharsets.UTF_8.name())
                        else URLDecoder.decode(path, StandardCharsets.UTF_8.name())
                    }

                    else -> null
                }
            }
        } catch (_: Exception) {
            classpathFromCodeSource()
        }
    }

    private fun classpathFromCodeSource(): List<String>? {
        return try {
            val codeSource = KttpConfiguration::class.java.protectionDomain?.codeSource?.location ?: return null
            val path = when (codeSource.protocol) {
                "file" -> URLDecoder.decode(codeSource.path, StandardCharsets.UTF_8.name())
                "jar" -> {
                    val p = codeSource.path
                    val sep = p.indexOf("!")
                    if (sep >= 0) URLDecoder.decode(p.substring(0, sep), StandardCharsets.UTF_8.name()) else null
                }

                else -> null
            }
            if (path != null) listOf(path) else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Lists sibling jars matching [libraryNames] beside this class's code source.
     * With the packaged plugin layout, that directory is the plugin's lib/ directory.
     */
    private fun fromPluginLibDirectory(): List<File> {
        return try {
            val codeSource =
                KttpScriptClasspath::class.java.protectionDomain?.codeSource?.location ?: return emptyList()
            val jarPath = when (codeSource.protocol) {
                "file" -> URLDecoder.decode(codeSource.path, StandardCharsets.UTF_8.name())
                "jar" -> {
                    val p = codeSource.path
                    val sep = p.indexOf("!")
                    if (sep >= 0) URLDecoder.decode(p.substring(0, sep), StandardCharsets.UTF_8.name()) else null
                }

                else -> null
            } ?: return emptyList()
            val dir = File(jarPath).parentFile ?: return emptyList()
            dir.listFiles { _, name -> name.endsWith(".jar") }?.asList().orEmpty()
                .filter { f -> libraryNames.any { lib -> lib in f.name } }
                .distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Resolves the plugin root through [PluginManager] / [PluginManagerCore]
     * and lists jars in its kttp-lib/ directory.
     */
    private fun fromPluginPathByPluginManager(): List<File> {
        return try {
            val pluginId = PluginId.getId(KTPP_PLUGIN_ID)

            @Suppress("DEPRECATION")
            val descriptor =
                PluginManager.getPlugin(pluginId) ?: PluginManagerCore.getPlugin(pluginId) ?: return emptyList()

            @Suppress("DEPRECATION")
            val pluginPath = descriptor.path ?: return emptyList()
            var pluginDir = (pluginPath as? java.nio.file.Path)?.toFile() ?: java.io.File(pluginPath.toString())
            if (pluginDir.isFile) pluginDir = pluginDir.parentFile?.parentFile ?: return emptyList()
            val kttpLibDir = File(pluginDir, "kttp-lib").takeIf { it.isDirectory } ?: return emptyList()
            kttpLibDir.listFiles { _, name -> name.endsWith(".jar") }?.asList().orEmpty().distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
