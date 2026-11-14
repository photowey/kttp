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

package io.github.photowey.kttp.script

import java.io.File
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Checks script-definition initialization on IDE worker threads without a context classloader.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/14
 */
class KttpConfigurationClassLoaderTest {
    @Test
    fun initializesWithoutAThreadContextClassLoader() {
        val thread = Thread.currentThread()
        val previous = thread.contextClassLoader
        try {
            thread.contextClassLoader = null
            val type =
                Class.forName("io.github.photowey.kttp.script.KttpConfiguration", true, FreshConfigurationLoader())
            val configuration = assertIs<ScriptCompilationConfiguration>(type.getField("INSTANCE").get(null))
            val classpath = configuration[ScriptCompilationConfiguration.dependencies].orEmpty()
                .filterIsInstance<JvmDependency>().flatMap { it.classpath }
            val templateLocation = File(Kttp::class.java.protectionDomain.codeSource.location.toURI()).canonicalFile
            assertTrue(
                classpath.any { it.canonicalFile == templateLocation },
                "The fallback must include the Kttp template"
            )
            assertNull(thread.contextClassLoader, "Initialization must not change the worker thread's loader")
        } finally {
            thread.contextClassLoader = previous
        }
    }

    /**
     * Reloads the configuration object so a previous successful initialization cannot hide the regression.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/14
     */
    private class FreshConfigurationLoader : ClassLoader(KttpConfigurationClassLoaderTest::class.java.classLoader) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (!name.startsWith("io.github.photowey.kttp.script.KttpConfiguration")) return super.loadClass(
                name,
                resolve
            )
            val loaded = findLoadedClass(name) ?: run {
                val resource = name.replace('.', '/') + ".class"
                val bytes = requireNotNull(parent.getResourceAsStream(resource)).use { it.readBytes() }
                defineClass(name, bytes, 0, bytes.size)
            }
            if (resolve) resolveClass(loaded)
            return loaded
        }
    }
}
