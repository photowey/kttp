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

package io.github.photowey.kttp.dsl

import io.github.photowey.kttp.core.context.execution.DeclarationCoordinates
import io.github.photowey.kttp.core.context.execution.ExecutionContext
import io.github.photowey.kttp.core.context.execution.ExecutionScenario
import io.github.photowey.kttp.core.context.execution.result.thenResponse
import io.github.photowey.kttp.core.context.store.LenientEnvironmentStore
import io.github.photowey.kttp.core.context.store.NoEnvironmentSelectedException
import io.github.photowey.kttp.core.context.store.StrictEnvironmentStore
import io.github.photowey.kttp.test.utils.ScriptStatement
import io.github.photowey.kttp.test.utils.testKttpContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Tests that DSL methods (GET, POST, etc.) register requests with correct coordinates.
 * Applies script blocks through [ScriptStatement.applyScript] and checks [ExecutionContext.registrations].
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class DslRegistrationTest {

    /**
     * Checks index and explicit-name coordinates, including a request with a response callback.
     */
    @Test
    fun `test registrations`() {
        val scriptStatement = ScriptStatement()
        scriptStatement.applyScript {
            GET("http://example.com/foo", "foo")
            GET("http://example.com/bar", "bar")
            GET("http://example.com/foo")
            GET("http://example.com/foo", "explicit-name")
            GET("http://example.com/foo").thenResponse { body!!.string() }
        }
        val executionContext = scriptStatement.context.executionContext
        val registrations = executionContext.registrations
            .map { it.coordinates }
            .flatten()
            .toSet()
        assertEquals(
            setOf(
                DeclarationCoordinates(0),
                DeclarationCoordinates(1),
                DeclarationCoordinates(2),
                DeclarationCoordinates(3),
                DeclarationCoordinates(4),
                DeclarationCoordinates("foo"),
                DeclarationCoordinates("bar"),
                DeclarationCoordinates("explicit-name"),
            ),
            registrations
        )
        executionContext.apply {
            assertRegistrationFound(DeclarationCoordinates("foo"))
            assertRegistrationFound(DeclarationCoordinates("explicit-name"))
        }
    }

    private fun ExecutionContext.assertRegistrationFound(declarationCoordinates: DeclarationCoordinates) {
        val registration = findRegistration(declarationCoordinates)
        assertNotNull(registration)
    }

    @Test
    fun `GET POST PUT register with index coordinates`() {
        val scriptStatement = ScriptStatement()
        scriptStatement.applyScript {
            GET("http://example.com/foo")
            POST("http://example.com/bar")
            PUT("http://example.com/baz")
        }

        val registrations = scriptStatement.context.executionContext.registrations
        assertEquals(3, registrations.size)
        assertEquals(setOf<DeclarationCoordinates>(DeclarationCoordinates(0)), registrations[0].coordinates)
        assertEquals(setOf<DeclarationCoordinates>(DeclarationCoordinates(1)), registrations[1].coordinates)
        assertEquals(setOf<DeclarationCoordinates>(DeclarationCoordinates(2)), registrations[2].coordinates)
    }

    @Test
    fun `GET with name registers both index and name coordinates`() {
        val scriptStatement = ScriptStatement()
        scriptStatement.applyScript {
            GET("http://example.com/foo", "foo")
            GET("http://example.com/bar", "bar")
        }

        val registrations = scriptStatement.context.executionContext.registrations
        assertEquals(2, registrations.size)
        assertEquals(
            setOf<DeclarationCoordinates>(DeclarationCoordinates(0), DeclarationCoordinates("foo")),
            registrations[0].coordinates
        )
        assertEquals(
            setOf<DeclarationCoordinates>(DeclarationCoordinates(1), DeclarationCoordinates("bar")),
            registrations[1].coordinates
        )
    }

    @Test
    fun `all HTTP methods register via request`() {
        val scriptStatement = ScriptStatement()
        val url = "http://example.com/"
        scriptStatement.applyScript {
            GET(url)
            POST(url)
            PUT(url)
            PATCH(url)
            DELETE(url)
            HEAD(url)
            OPTIONS(url)
            TRACE(url)
        }

        val registrations = scriptStatement.context.executionContext.registrations
        assertEquals(8, registrations.size)
        registrations.forEachIndexed { index, reg ->
            assertEquals(setOf<DeclarationCoordinates>(DeclarationCoordinates(index)), reg.coordinates)
        }
    }

    @Test
    fun `findRegistration returns registration by name`() {
        val scriptStatement = ScriptStatement()
        scriptStatement.applyScript {
            GET("http://example.com/a", "first")
            GET("http://example.com/b", "second")
        }

        val reg = scriptStatement.context.executionContext.findRegistration(DeclarationCoordinates("first"))
        assertNotNull(reg)
        assertEquals(
            setOf<DeclarationCoordinates>(DeclarationCoordinates(0), DeclarationCoordinates("first")),
            reg.coordinates
        )
    }

    @Test
    fun `execute throws NoEnvironmentSelectedException when request used unset env keys`() {
        val store = LenientEnvironmentStore(StrictEnvironmentStore(emptyMap()))
        val context = testKttpContext(environmentStore = store)
        ScriptStatement(context).applyScript {
            val baseUrl: String by env
            GET("$baseUrl/api")
        }
        assertEquals(1, context.executionContext.registrations.size)
        assertEquals(setOf("baseUrl"), context.executionContext.registrations.single().envKeysUsedUnset)
        assertFailsWith<NoEnvironmentSelectedException> {
            context.executionContext.execute(ExecutionScenario.File)
        }
    }

    @Test
    fun `execute SingleExecution by index throws when that registration has unset env keys`() {
        val store = LenientEnvironmentStore(StrictEnvironmentStore(emptyMap()))
        val context = testKttpContext(environmentStore = store)
        ScriptStatement(context).applyScript {
            GET("http://example.com/first")
            val baseUrl: String by env
            GET("$baseUrl/api")
        }
        assertEquals(2, context.executionContext.registrations.size)
        assertEquals(emptySet(), context.executionContext.registrations[0].envKeysUsedUnset)
        assertEquals(setOf("baseUrl"), context.executionContext.registrations[1].envKeysUsedUnset)
        assertFailsWith<NoEnvironmentSelectedException> {
            context.executionContext.execute(ExecutionScenario.SingleExecution(DeclarationCoordinates(1)))
        }
    }

    @Test
    fun `first request no env second request uses unset env - per registration envKeysUsedUnset`() {
        val store = LenientEnvironmentStore(StrictEnvironmentStore(emptyMap()))
        val context = testKttpContext(environmentStore = store)
        ScriptStatement(context).applyScript {
            GET("http://example.com/first")
            val baseUrl: String by env
            GET("$baseUrl/api")
        }
        val regs = context.executionContext.registrations
        assertEquals(2, regs.size)
        assertEquals(emptySet<String>(), regs[0].envKeysUsedUnset)
        assertEquals(setOf("baseUrl"), regs[1].envKeysUsedUnset)
    }

    @Test
    fun `two requests both use same unset env - both registrations have envKeysUsedUnset`() {
        val store = LenientEnvironmentStore(StrictEnvironmentStore(emptyMap()))
        val context = testKttpContext(environmentStore = store)
        ScriptStatement(context).applyScript {
            val baseUrl: String by env
            GET("$baseUrl/a")
            GET("$baseUrl/b")
        }
        val regs = context.executionContext.registrations
        assertEquals(2, regs.size)
        assertEquals(setOf("baseUrl"), regs[0].envKeysUsedUnset)
        assertEquals(setOf("baseUrl"), regs[1].envKeysUsedUnset)
    }

    @Test
    fun `one request uses two unset env vars - envKeysUsedUnset contains both`() {
        val store = LenientEnvironmentStore(StrictEnvironmentStore(emptyMap()))
        val context = testKttpContext(environmentStore = store)
        ScriptStatement(context).applyScript {
            val baseUrl: String by env
            val path: String by env
            GET("$baseUrl$path")
        }
        val regs = context.executionContext.registrations
        assertEquals(1, regs.size)
        assertEquals(setOf("baseUrl", "path"), regs[0].envKeysUsedUnset)
    }

    @Test
    fun `lenient store with env var set - registration has empty envKeysUsedUnset`() {
        val store = LenientEnvironmentStore(StrictEnvironmentStore(mapOf("baseUrl" to "http://host")))
        val context = testKttpContext(environmentStore = store)
        ScriptStatement(context).applyScript {
            val baseUrl: String by env
            GET("$baseUrl/api")
        }
        val regs = context.executionContext.registrations
        assertEquals(1, regs.size)
        assertEquals(emptySet<String>(), regs[0].envKeysUsedUnset)
    }

    @Test
    fun `three requests only middle uses unset env - envKeysUsedUnset per registration`() {
        val store = LenientEnvironmentStore(StrictEnvironmentStore(emptyMap()))
        val context = testKttpContext(environmentStore = store)
        ScriptStatement(context).applyScript {
            GET("http://example.com/1")
            val baseUrl: String by env
            GET("$baseUrl/api")
            GET("http://example.com/3")
        }
        val regs = context.executionContext.registrations
        assertEquals(3, regs.size)
        assertEquals(emptySet<String>(), regs[0].envKeysUsedUnset)
        assertEquals(setOf("baseUrl"), regs[1].envKeysUsedUnset)
        assertEquals(emptySet<String>(), regs[2].envKeysUsedUnset)
    }
}
