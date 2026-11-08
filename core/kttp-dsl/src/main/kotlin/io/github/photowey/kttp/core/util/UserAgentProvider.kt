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

package io.github.photowey.kttp.core.util

import kotlin.KotlinVersion

/**
 * Provides User-Agent string for Kttp requests.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/17
 */
object UserAgentProvider {

    private val kotlinVersion: String by lazy {
        KotlinVersion.CURRENT.toString()
    }

    private val kttpVersion: String by lazy {
        // Try to read from system property first (set by plugin)
        val rawVersion = System.getProperty("kttp.version")
            ?: readVersionFromResource()
            ?: "1.0.0"
        // Remove SNAPSHOT suffix for User-Agent
        rawVersion.removeSuffix("-SNAPSHOT")
    }

    /**
     * Returns the User-Agent string.
     * Format: Kotlin/{kotlinVersion} Kttp/{kttpVersion}
     */
    fun userAgent(): String {
        return "Kotlin/$kotlinVersion Kttp/$kttpVersion"
    }

    /**
     * Reads Kttp version from package resource.
     */
    private fun readVersionFromResource(): String? {
        return try {
            val resource = javaClass.getResource("/kttp-version.properties")
            if (resource != null) {
                java.io.InputStreamReader(resource.openStream(), Charsets.UTF_8).use { reader ->
                    java.util.Properties().apply {
                        load(reader)
                    }.getProperty("version")
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
