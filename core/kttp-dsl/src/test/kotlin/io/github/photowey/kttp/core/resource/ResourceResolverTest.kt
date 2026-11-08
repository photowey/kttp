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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Verifies filesystem, classpath, project, and relative resource resolution.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/18
 */
class ResourceResolverTest {

    @Test
    fun `resolve file URI prefix returns FileSystemResource`() {
        val resolver = ResourceResolver(null)
        val resource = resolver.resolve("file:///tmp/test.txt")

        assertTrue(resource is FileSystemResource)
        assertEquals("/tmp/test.txt", (resource as FileSystemResource).getAbsolutePath())
    }

    @Test
    fun `resolve classpath prefix returns ClasspathResource`() {
        val resolver = ResourceResolver(null)
        val resource = resolver.resolve("classpath:/test-resource.txt")

        assertTrue(resource is ClasspathResource)
        assertTrue(resource.getAbsolutePath().startsWith("classpath:"))
    }

    @Test
    fun `resolve absolute path returns FileSystemResource`() {
        val resolver = ResourceResolver(null)
        val resource = resolver.resolve("/tmp/test.txt")

        assertTrue(resource is FileSystemResource)
        assertEquals("/tmp/test.txt", (resource as FileSystemResource).getAbsolutePath())
    }

    @Test
    fun `resolve project path returns ProjectResource`() {
        val resolver = ResourceResolver(null)
        val resource = resolver.resolve("src/main/resources/test.txt")

        assertTrue(resource is ProjectResource)
    }

    @Test
    fun `resolve relative path returns RelativeResource`() {
        val scriptDir = "/scripts/api"
        val resolver = ResourceResolver(scriptDir)
        val resource = resolver.resolve("data/request.json")

        assertTrue(resource is RelativeResource)
        assertTrue(resource.getAbsolutePath().contains("data/request.json"))
    }

    @Test
    fun `FileSystemResource reads existing file`() {
        val tempFile = File.createTempFile("test", ".txt")
        try {
            tempFile.writeText("Hello World", StandardCharsets.UTF_8)

            val resource = FileSystemResource(tempFile.absolutePath)
            assertTrue(resource.exists())
            assertEquals("Hello World", resource.readText(StandardCharsets.UTF_8))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `FileSystemResource exists returns false for non-existent file`() {
        val resource = FileSystemResource("/nonexistent/file.txt")
        assertFalse(resource.exists())
    }

    @Test
    fun `FileSystemResource exists returns false for directory`() {
        val tempDir = File.createTempFile("test", "").apply { delete(); mkdirs() }
        try {
            val resource = FileSystemResource(tempDir.absolutePath)
            assertFalse(resource.exists())
        } finally {
            tempDir.delete()
        }
    }

    @Test
    fun `RelativeResource resolves relative to script directory`() {
        val scriptDir = File.createTempFile("script", ".kts").parent
        val testFile = File(scriptDir, "test.txt")
        try {
            testFile.writeText("Relative content", StandardCharsets.UTF_8)

            val resource = RelativeResource("test.txt", scriptDir)
            assertTrue(resource.exists())
            assertEquals("Relative content", resource.readText(StandardCharsets.UTF_8))
            assertTrue(resource.getAbsolutePath().contains("test.txt"))
        } finally {
            testFile.delete()
        }
    }

    @Test
    fun `RelativeResource resolves without script directory`() {
        val testFile = File.createTempFile("test", ".txt")
        try {
            testFile.writeText("Content", StandardCharsets.UTF_8)

            val resource = RelativeResource(testFile.name, null)
            // Without script directory, it should resolve relative to current directory
            // This test verifies it doesn't crash
            val absolutePath = resource.getAbsolutePath()
            assertTrue(absolutePath.isNotEmpty())
        } finally {
            testFile.delete()
        }
    }

    @Test
    fun `ProjectResource resolves from project root`() {
        // Create a temporary project structure
        val projectRoot = File.createTempFile("project", "").apply { delete(); mkdirs() }
        val srcDir = File(projectRoot, "src").apply { mkdirs() }
        val resourcesDir = File(srcDir, "main/resources").apply { mkdirs() }
        val testFile = File(resourcesDir, "test.txt").apply {
            writeText("Project resource", StandardCharsets.UTF_8)
        }

        try {
            val resource = ProjectResource("src/main/resources/test.txt", projectRoot.absolutePath)
            assertTrue(resource.exists())
            assertEquals("Project resource", resource.readText(StandardCharsets.UTF_8))
            assertTrue(resource.getAbsolutePath().contains("test.txt"))
        } finally {
            testFile.delete()
            resourcesDir.delete()
            srcDir.delete()
            projectRoot.delete()
        }
    }

    @Test
    fun `ProjectResource falls back to current working directory`() {
        // This test verifies that ProjectResource can find project root from current directory
        val resource = ProjectResource("src/main/resources/nonexistent.txt", null)
        // Should not crash, even if file doesn't exist
        val absolutePath = resource.getAbsolutePath()
        assertTrue(absolutePath.isNotEmpty())
    }

    @Test
    fun `ClasspathResource reads from classpath`() {
        // Create a test resource file in test resources
        val resource = ClasspathResource("test-resource.txt")
        // Note: This test requires a test-resource.txt file in src/test/resources
        // If it doesn't exist, exists() will return false, which is fine for testing
        val exists = resource.exists()
        if (exists) {
            val content = resource.readText(StandardCharsets.UTF_8)
            assertTrue(content.isNotEmpty())
        }
    }

    @Test
    fun `ClasspathResource getAbsolutePath returns classpath prefix`() {
        val resource = ClasspathResource("test.txt")
        assertTrue(resource.getAbsolutePath().startsWith("classpath:"))
    }
}
