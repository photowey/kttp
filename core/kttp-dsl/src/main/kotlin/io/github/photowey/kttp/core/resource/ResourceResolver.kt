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

package io.github.photowey.kttp.core.resource

import java.io.File
import java.io.InputStream

/**
 * Represents a resource that can be read.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/18
 */
sealed interface Resource {
    /**
     * Returns an absolute file path, or a `classpath:/...` identifier for a JVM classpath resource.
     */
    fun getAbsolutePath(): String

    /**
     * Checks if the resource exists.
     */
    fun exists(): Boolean

    /**
     * Reads the resource content as a string.
     */
    fun readText(charset: java.nio.charset.Charset): String

    /**
     * Reads the resource content as bytes.
     */
    fun readBytes(): ByteArray
}

/**
 * Reads a filesystem resource through java.io.File; existence requires a regular file.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/18
 */
class FileSystemResource(private val path: String) : Resource {
    private val file = File(path)

    override fun getAbsolutePath(): String = file.absolutePath

    override fun exists(): Boolean = file.exists() && file.isFile

    override fun readText(charset: java.nio.charset.Charset): String {
        return file.readText(charset)
    }

    override fun readBytes(): ByteArray {
        return file.readBytes()
    }
}

/**
 * Reads a JVM classpath resource using the thread context classloader, falling back to its own classloader.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/18
 */
class ClasspathResource(private val path: String) : Resource {
    private val resourcePath = if (path.startsWith("/")) path else "/$path"

    override fun getAbsolutePath(): String = "classpath:$resourcePath"

    override fun exists(): Boolean {
        // Use thread context classloader or fallback to this class's classloader
        val classLoader = Thread.currentThread().contextClassLoader
            ?: ClasspathResource::class.java.classLoader
        return classLoader.getResource(resourcePath.removePrefix("/")) != null
    }

    override fun readText(charset: java.nio.charset.Charset): String {
        val classLoader = Thread.currentThread().contextClassLoader
            ?: ClasspathResource::class.java.classLoader
        val inputStream = classLoader.getResourceAsStream(resourcePath.removePrefix("/"))
            ?: throw IllegalStateException("Classpath resource not found: $resourcePath")
        val bytes = inputStream.use { it.readBytes() }
        return String(bytes, charset)
    }

    override fun readBytes(): ByteArray {
        val classLoader = Thread.currentThread().contextClassLoader
            ?: ClasspathResource::class.java.classLoader
        val inputStream = classLoader.getResourceAsStream(resourcePath.removePrefix("/"))
            ?: throw IllegalStateException("Classpath resource not found: $resourcePath")
        return inputStream.use { it.readBytes() }
    }
}

/**
 * Resolves a path containing src/ by searching ancestors from the supplied base and working directory.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/18
 */
class ProjectResource(private val path: String, private val scriptDirectory: String?) : Resource {
    private val file: File by lazy {
        resolveProjectPath(path) ?: File(path)
    }

    override fun getAbsolutePath(): String = file.absolutePath

    override fun exists(): Boolean = file.exists() && file.isFile

    override fun readText(charset: java.nio.charset.Charset): String {
        return file.readText(charset)
    }

    override fun readBytes(): ByteArray {
        return file.readBytes()
    }

    /**
     * Resolves project path by finding project root (directory containing src/).
     */
    private fun resolveProjectPath(path: String): File? {
        // Start from script directory if available, otherwise current working directory
        val startDir = scriptDirectory?.let { File(it) } ?: File(System.getProperty("user.dir"))
        var currentDir: File? = startDir

        // Walk up the directory tree to find project root
        while (currentDir != null && currentDir.exists()) {
            val srcDir = File(currentDir, "src")
            if (srcDir.exists() && srcDir.isDirectory) {
                // Found project root
                return File(currentDir, path)
            }
            currentDir = currentDir.parentFile
        }

        // If not found, try from current working directory
        if (scriptDirectory != null) {
            currentDir = File(System.getProperty("user.dir"))
            while (currentDir != null && currentDir.exists()) {
                val srcDir = File(currentDir, "src")
                if (srcDir.exists() && srcDir.isDirectory) {
                    return File(currentDir, path)
                }
                currentDir = currentDir.parentFile
            }
        }

        return null
    }
}

/**
 * Relative resource resolved against the supplied base directory, or the working directory when absent.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/18
 */
class RelativeResource(private val path: String, private val scriptDirectory: String?) : Resource {
    private val file: File by lazy {
        if (scriptDirectory != null) {
            File(scriptDirectory, path)
        } else {
            File(path)
        }
    }

    override fun getAbsolutePath(): String = file.absolutePath

    override fun exists(): Boolean = file.exists() && file.isFile

    override fun readText(charset: java.nio.charset.Charset): String {
        return file.readText(charset)
    }

    override fun readBytes(): ByteArray {
        return file.readBytes()
    }
}

/**
 * Resource resolver that resolves paths using Spring Resource-style patterns.
 *
 * Priority order:
 * 1. file:// prefix → file system path after removing the prefix
 * 2. classpath:/ prefix → a non-empty resource path with a supplied base directory resolves as
 *    baseDir/src/main/resources/...; otherwise it is a JVM classpath resource. The supplied base may
 *    be an explicit runner base directory or the script directory; this resolver does not distinguish them.
 * 3. Absolute path (starts with /) → file system absolute path (preferred form; equivalent to file://)
 * 4. Project path (contains src/) → searches ancestors for a directory containing src/,
 *    starting at the supplied base and then the working directory; falls back to the working directory
 * 5. Relative path → resolved relative to the supplied base directory, or the working directory when absent
 *
 * Absolute path can be written as `/home/xxx/kttp/hello.json`; the `file://` prefix is optional.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/18
 */
class ResourceResolver(private val scriptDirectory: String?) {
    /**
     * Resolves a path to a Resource.
     *
     * @param path The path to resolve
     * @return A Resource instance
     */
    fun resolve(path: String): Resource {
        return when {
            path.startsWith("file://") -> {
                val filePath = path.removePrefix("file://")
                FileSystemResource(filePath)
            }

            path.startsWith("classpath:/") -> {
                val segment = path.removePrefix("classpath:/").trimStart('/')
                if (scriptDirectory != null && segment.isNotEmpty()) {
                    // The supplied base can be the project root or the script directory.
                    FileSystemResource(File(scriptDirectory, "src/main/resources/$segment").absolutePath)
                } else {
                    ClasspathResource(if (segment.isEmpty()) "" else segment)
                }
            }

            path.startsWith("/") -> {
                // Absolute path
                FileSystemResource(path)
            }

            path.contains("src/") -> {
                // Project path
                ProjectResource(path, scriptDirectory)
            }

            else -> {
                // Relative path
                RelativeResource(path, scriptDirectory)
            }
        }
    }
}
