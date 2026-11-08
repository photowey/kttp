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

package io.github.photowey.kttp.cli.execution

import io.github.photowey.kttp.core.context.ClientContextImpl
import io.github.photowey.kttp.core.context.KttpContextImpl
import io.github.photowey.kttp.core.context.NoopCookiesContext
import io.github.photowey.kttp.core.context.createKttpContext
import io.github.photowey.kttp.core.context.execution.DeclarationCoordinates
import io.github.photowey.kttp.core.context.storage.InMemoryStorage
import io.github.photowey.kttp.core.context.store.NoopEnvironmentStore
import io.github.photowey.kttp.core.interceptor.KttpInterceptor
import io.github.photowey.kttp.core.printer.NoopPrinter
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests DSL registration via script evaluation (using KttpScriptEvaluator).
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/18
 */
class KttpScriptEvaluatorDslTest {

    private fun testKttpContext(): KttpContextImpl {
        val printer = NoopPrinter
        val clientContext = ClientContextImpl(KttpInterceptor(printer, null))
        return createKttpContext(
            storage = InMemoryStorage(),
            environmentStore = NoopEnvironmentStore,
            cookiesContext = NoopCookiesContext,
            clientContext = clientContext,
            printer = printer,
        )
    }

    @Test
    fun `runScript GET and POST registers two requests`() {
        val context = testKttpContext()
        val script = """
            GET("http://example.com/foo")
            POST("http://example.com/bar") { }
        """.trimIndent()
        val result = KttpScriptEvaluator.evaluate(script, "test.kttp.kts", context)
        assertTrue(
            result is ResultWithDiagnostics.Success,
            (result as? ResultWithDiagnostics.Failure)?.reports?.joinToString("\n") ?: "eval failed"
        )
        assertEquals(2, context.executionContext.registrations.size)
    }

    @Test
    fun `runScript GET with name registers with name coordinate`() {
        val context = testKttpContext()
        val script = """
            GET("http://example.com/a", "first")
            GET("http://example.com/b", "second")
        """.trimIndent()
        val result = KttpScriptEvaluator.evaluate(script, "test.kttp.kts", context)
        assertTrue(result is ResultWithDiagnostics.Success)
        val reg = context.executionContext.findRegistration(DeclarationCoordinates("first"))
        assertNotNull(reg)
        assertEquals(
            setOf<DeclarationCoordinates>(DeclarationCoordinates(0), DeclarationCoordinates("first")),
            reg.coordinates
        )
    }
}
