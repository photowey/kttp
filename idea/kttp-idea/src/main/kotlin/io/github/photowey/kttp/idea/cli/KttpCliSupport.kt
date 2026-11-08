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

package io.github.photowey.kttp.idea.cli

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import io.github.photowey.kttp.idea.execution.KttpExecutionResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Shared CLI argument model for script-based IDEA entry points.
 * Keeps argument construction consistent across Run Configuration,
 * GUI Follow Script, and Copy as cURL.
 * GUI Quick Request uses its direct HTTP executor instead.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
data class KttpCliInvocation(
    val scriptPath: String,
    val requestNumber: Int? = null,
    val requestName: String? = null,
    val envVariables: Map<String, String> = emptyMap(),
    val jsonOutput: Boolean = false,
    val responseDir: String? = null,
    val baseDir: String? = null,
    val executionMode: String? = null,
    val compilationCache: Boolean = true,
    val responseManifest: String? = null,
    val responseSnapshot: String? = null,
    val requestLines: String? = null
) {

    /** Builds argument tokens without shell quoting and sorts environment keys for stable invocation construction. */
    fun toProgramArguments(): List<String> = buildList {
        add("--script")
        add(scriptPath)

        if (jsonOutput) {
            add("--json-output")
        }

        add(if (compilationCache) "--compilation-cache" else "--no-compilation-cache")

        responseDir?.let {
            add("--response-dir")
            add(it)
        }

        responseManifest?.let {
            add("--response-manifest")
            add(it)
        }

        responseSnapshot?.let {
            add("--response-snapshot")
            add(it)
        }

        baseDir?.let {
            add("--base-dir")
            add(it)
        }

        requestLines?.let {
            add("--request-lines")
            add(it)
        }

        requestNumber?.let {
            add("--request-number")
            add(it.toString())
        }

        requestName?.let {
            add("--request-name")
            add(it)
        }

        executionMode?.let {
            add("--execution-mode")
            add(it)
        }

        envVariables.toSortedMap().forEach { (key, value) ->
            add("--env-param")
            add("$key=$value")
        }
    }
}

/**
 * Locates the CLI JAR in an installed plugin or a development build.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
object KttpCliLocator {

    private const val PLUGIN_ID = "io.github.photowey.kttp"

    /**
     * Finds a CLI JAR from an explicit override, plugin installation, development builds, or the user install directory.
     * Returns null when none exists; installed plugins are expected to include cli/kttp-cli.jar.
     */
    fun findCliJar(projectBasePath: String? = null): File? {
        System.getProperty("kttp.cli.jar")?.let {
            val file = File(it)
            if (file.isFile) return file
        }

        System.getProperty("plugin.path")?.let {
            normalizePluginRoot(File(it))?.let { pluginRoot ->
                findPackagedCliJar(pluginRoot)?.let { cliJar ->
                    return cliJar
                }
                findDevelopmentCliJarFromAncestors(pluginRoot)?.let { cliJar ->
                    return cliJar
                }
            }
        }

        try {
            val pluginPath = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))?.pluginPath?.toFile()
            if (pluginPath != null) {
                normalizePluginRoot(pluginPath)?.let { pluginRoot ->
                    findPackagedCliJar(pluginRoot)?.let { cliJar ->
                        return cliJar
                    }
                    findDevelopmentCliJarFromAncestors(pluginRoot)?.let { cliJar ->
                        return cliJar
                    }
                }
            }
        } catch (_: Exception) {
            // Plugin metadata may be unavailable during early startup or isolated tests.
        }

        projectBasePath?.let {
            findDevelopmentCliJar(File(it))?.let { cliJar ->
                return cliJar
            }
        }

        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null && dir.exists()) {
            findDevelopmentCliJar(dir)?.let { cliJar ->
                return cliJar
            }
            dir = dir.parentFile
        }

        val installDir = File(System.getProperty("user.home"), ".local/share/kttp/")
        if (installDir.exists()) {
            installDir.listFiles { f -> f.name == "kttp-cli.jar" }?.firstOrNull()?.let {
                return it
            }
        }

        return null
    }

    internal fun findPackagedCliJar(pluginRoot: File): File? = sequenceOf(
        File(pluginRoot, "cli/kttp-cli.jar"),
        File(pluginRoot, "kttp-cli.jar")
    ).firstOrNull { it.isFile }

    internal fun findDevelopmentCliJar(projectRoot: File): File? {
        val libsDir = File(projectRoot, "core/kttp-cli/build/libs")
        if (!libsDir.isDirectory) return null

        val exactJar = File(libsDir, "kttp-cli.jar")
        if (exactJar.isFile) return exactJar

        return libsDir.listFiles { file ->
            file.isFile && file.name.startsWith("kttp-cli-") && file.name.endsWith(".jar")
                && listOf("-plain.jar", "-sources.jar", "-javadoc.jar").none { file.name.endsWith(it) }
        }?.sortedBy { it.name }?.firstOrNull()
    }

    internal fun findDevelopmentCliJarFromAncestors(startDir: File): File? {
        var dir: File? = startDir
        while (dir != null) {
            findDevelopmentCliJar(dir)?.let { return it }
            dir = dir.parentFile
        }
        return null
    }

    private fun normalizePluginRoot(path: File): File? = when {
        path.isDirectory -> path
        path.isFile -> path.parentFile?.parentFile
        else -> null
    }
}

/**
 * Extracts structured CLI results from process output for IDEA consumers.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
object KttpCliJsonResultParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Decodes the last JSON-looking output line, retaining HTTP details even for a failed CLI result.
     * Invalid output becomes a failed result with the original output as its body.
     */
    fun parse(scriptName: String, output: String): KttpExecutionResult {
        return try {
            val jsonLine = output.trim().lines().lastOrNull { it.startsWith("{") } ?: output.trim()
            val cliResult = json.decodeFromString(CliExecutionResult.serializer(), jsonLine)

            val displayMessage = when {
                !cliResult.success && !cliResult.error.isNullOrBlank() -> cliResult.error
                else -> cliResult.message
            } ?: "Unknown CLI result"

            KttpExecutionResult(
                scriptName = cliResult.scriptName,
                success = cliResult.success,
                message = displayMessage,
                statusCode = cliResult.statusCode,
                protocol = cliResult.protocol,
                headers = cliResult.headers,
                body = cliResult.body,
                contentType = cliResult.contentType,
                bodyType = cliResult.bodyType,
                responseFilePath = cliResult.responseFilePath,
                requestMethod = cliResult.requestMethod,
                requestUrl = cliResult.requestUrl,
                requestHeaders = cliResult.requestHeaders,
                requestBody = cliResult.requestBody,
                bodyTruncated = cliResult.bodyTruncated,
                responseSize = cliResult.responseSize,
                httpTimeMs = cliResult.httpTimeMs,
                assertionsPassed = cliResult.success
            )
        } catch (e: Exception) {
            KttpExecutionResult(
                scriptName = scriptName,
                success = false,
                message = "Failed to parse CLI output: ${e.message}",
                body = output
            )
        }
    }

    /**
     * Represents the serialized CLI result fields consumed by the IDEA plugin.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    @Serializable
    private data class CliExecutionResult(
        val success: Boolean,
        val scriptName: String,
        val message: String,
        val statusCode: Int? = null,
        val statusMessage: String? = null,
        val protocol: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null,
        val contentType: String? = null,
        val bodyType: String? = null,
        val responseFilePath: String? = null,
        val requestMethod: String? = null,
        val requestUrl: String? = null,
        val requestHeaders: Map<String, String> = emptyMap(),
        val requestBody: String? = null,
        val executionTimeMs: Long? = null,
        val error: String? = null,
        val bodyTruncated: Boolean = false,
        val responseSize: Long? = null,
        val httpTimeMs: Long? = null
    )
}
