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

package io.github.photowey.kttp.idea.gui

import org.junit.Test
import kotlin.test.assertEquals

/**
 * Verifies parsing and display of environment and local-file value references.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class GuiValueRefCodecTest {

    @Test
    fun parseRecognizesEnvPlaceholder() {
        assertEquals(ValueRef.Env("API_TOKEN"), GuiValueRefCodec.parse("\${API_TOKEN}"))
        assertEquals(ValueRef.Env("API_TOKEN"), GuiValueRefCodec.parse("\${env:API_TOKEN}"))
    }

    @Test
    fun onlyCompleteValidPlaceholdersBecomeEnvironmentReferences() {
        listOf("baseUrl", "API_TOKEN", "service.name", "service-name", "item_1").forEach { key ->
            assertEquals(ValueRef.Env(key), GuiValueRefCodec.parse(" \${$key} "))
        }
        listOf(
            "\${}",
            "\${env:}",
            "\${env:token:bad}",
            "\${two words}",
            "\${baseUrl}/users",
            "Bearer \${TOKEN}"
        ).forEach {
            assertEquals(ValueRef.Literal(it), GuiValueRefCodec.parse(it))
        }
    }

    @Test
    fun literalEscapePreventsEnvironmentResolution() {
        assertEquals(ValueRef.Literal("\${TOKEN}"), GuiValueRefCodec.parse("\\\${TOKEN}"))
        assertEquals(ValueRef.Literal("\${env:TOKEN}"), GuiValueRefCodec.parse("\\\${env:TOKEN}"))
        assertEquals("\\\${TOKEN}", GuiValueRefCodec.toDisplayValue(ValueRef.Literal("\${TOKEN}")))
    }

    @Test
    fun parseRecognizesLocalFilePlaceholderOnlyWhenAllowed() {
        assertEquals(
            ValueRef.LocalFile("request.json"),
            GuiValueRefCodec.parse("@file:request.json", allowLocalFile = true)
        )
        assertEquals(
            ValueRef.Literal("@file:request.json"),
            GuiValueRefCodec.parse("@file:request.json", allowLocalFile = false)
        )
    }

    @Test
    fun displayRoundTripsSupportedValueRefs() {
        assertEquals("\${BASE_URL}", GuiValueRefCodec.toDisplayValue(ValueRef.Env("BASE_URL")))
        assertEquals("@file:payload.json", GuiValueRefCodec.toDisplayValue(ValueRef.LocalFile("payload.json")))
        assertEquals("hello", GuiValueRefCodec.toDisplayValue(ValueRef.Literal("hello")))
    }
}
