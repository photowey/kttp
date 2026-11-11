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

import io.github.photowey.kttp.core.constant.DEFAULT_USER_AGENT
import io.github.photowey.kttp.dsl.builder.RequestBuilder
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Verifies build-version metadata and default request identification without overriding kttp.version.
 *
 * @author photowey
 * @version 1.0.1
 * @since 2026/11/12
 */
class UserAgentProviderTest {
    private val expectedVersion = requireNotNull(System.getProperty("kttp.test.expectedVersion"))
    private val expectedUserAgent = "Kotlin/${KotlinVersion.CURRENT} Kttp/$expectedVersion"

    @Test
    fun `version resource contains the Gradle project version`() {
        val stream = assertNotNull(UserAgentProvider::class.java.getResourceAsStream("/kttp-version.properties"))
        val properties = stream.reader(Charsets.UTF_8).use { reader -> Properties().apply { load(reader) } }
        assertEquals(expectedVersion, properties.getProperty("version").removeSuffix("-SNAPSHOT"))
    }

    @Test
    fun `default request identifies the current build`() {
        val request = RequestBuilder("GET", "http://localhost/", null).build()
        assertEquals(expectedUserAgent, request.header("User-Agent"))
    }

    @Test
    fun `default header accessor uses the same build metadata`() {
        assertEquals(expectedUserAgent, DEFAULT_USER_AGENT)
    }
}
