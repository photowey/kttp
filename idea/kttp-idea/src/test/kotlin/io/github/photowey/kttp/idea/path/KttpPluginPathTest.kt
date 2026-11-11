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

package io.github.photowey.kttp.idea.path

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URL
import java.security.CodeSource
import java.security.ProtectionDomain
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Verifies installed-plugin lookup with class loaders that omit JAR locations from protection domains.
 *
 * @author photowey
 * @version 1.0.1
 * @since 2026/11/11
 */
class KttpPluginPathTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun resolvesPackagedClassWhenCodeSourceIsMissing() {
        val root = temporary.newFolder("plugin + 空格")
        val loaded = packagedResolver(root, null)
        assertNull(loaded.protectionDomain.codeSource)
        assertEquals(root, installedRoot(loaded))
    }

    @Test
    fun resolvesPackagedClassWhenCodeSourcePointsToPluginDirectory() {
        val root = temporary.newFolder("plugin directory")
        val loaded = packagedResolver(root, root.toURI().toURL())
        assertEquals(root, installedRoot(loaded))
    }

    @Test
    fun acceptsJarLocationsAndRejectsLooseClassesOutsidePluginLayout() {
        val root = temporary.newFolder("plugin")
        val jar = File(root, "lib/kttp-idea.jar").apply { parentFile.mkdirs(); writeBytes(byteArrayOf()) }
        assertEquals(root, KttpPluginPath.fromClassLocation(jar.toURI().toURL()))
        assertNull(KttpPluginPath.fromClassLocation(temporary.root.toURI().toURL()))
        assertNull(KttpPluginPath.fromClassLocation(null))
    }

    private fun installedRoot(type: Class<*>): File? =
        type.getMethod("installedRoot").invoke(type.getField("INSTANCE").get(null)) as File?

    private fun packagedResolver(root: File, source: URL?): Class<*> {
        val name = KttpPluginPath::class.java.name
        val entry = name.replace('.', '/') + ".class"
        val bytes = requireNotNull(KttpPluginPath::class.java.getResourceAsStream("/$entry")).use { it.readBytes() }
        val jar = File(root, "lib/kttp-idea.jar").apply { parentFile.mkdirs() }
        JarOutputStream(jar.outputStream()).use { output ->
            output.putNextEntry(JarEntry(entry))
            output.write(bytes)
            output.closeEntry()
        }
        return PackagedClassLoader(entry, URL("jar:${jar.toURI()}!/$entry"))
            .definePluginClass(name, bytes, source)
    }

    /**
     * Models packaged resources independently of the optional class protection-domain location.
     *
     * @author photowey
     * @version 1.0.1
     * @since 2026/11/11
     */
    private class PackagedClassLoader(private val entry: String, private val resource: URL) :
        ClassLoader(KttpPluginPath::class.java.classLoader) {
        fun definePluginClass(name: String, bytes: ByteArray, source: URL?): Class<*> =
            defineClass(name, bytes, 0, bytes.size, ProtectionDomain(source?.let { CodeSource(it, null as Array<java.security.cert.Certificate>?) }, null))

        override fun getResource(name: String): URL? = if (name == entry) resource else super.getResource(name)
    }
}
