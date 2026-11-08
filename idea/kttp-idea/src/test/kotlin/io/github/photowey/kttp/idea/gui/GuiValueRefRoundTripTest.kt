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
 * Verifies value-reference round trips without changing literal text or file metadata.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class GuiValueRefRoundTripTest {

    @Test
    fun literalEnvironmentPlaceholderRetainsLiteralType() {
        assertLiteralRoundTrip("\${env:TOKEN}")
        assertLiteralRoundTrip("  \${env:TOKEN}  ")
    }

    @Test
    fun literalFilePlaceholderRetainsLiteralType() {
        assertLiteralRoundTrip("@file:payload.json")
        assertLiteralRoundTrip(" @file:payload.json ")
    }

    @Test
    fun whitespaceLiteralsRetainEveryCharacter() {
        listOf("", " ", "\t\n", "  body  ").forEach(::assertLiteralRoundTrip)
    }

    @Test
    fun backslashesDoNotCollideWithLiteralEscapes() {
        listOf(
            "\\",
            "\\\${env:TOKEN}",
            "\\@file:payload.json",
            "C:\\data\\payload.json",
            "a\\b"
        ).forEach(::assertLiteralRoundTrip)
    }

    @Test
    fun handwrittenReferencesRemainAvailable() {
        assertEquals(ValueRef.Env("TOKEN"), GuiValueRefCodec.parse(" \${env:TOKEN} "))
        assertEquals(ValueRef.LocalFile("payload.json"), GuiValueRefCodec.parse(" @file:payload.json ", true))
        assertEquals(ValueRef.Literal("@file:payload.json"), GuiValueRefCodec.parse("@file:payload.json"))
    }

    @Test
    fun ordinaryTextNeedsNoEscape() {
        listOf("hello", "https://example.com", " body ", "C:\\data\\payload.json", "@file:").forEach {
            assertEquals(it, GuiValueRefCodec.toDisplayValue(ValueRef.Literal(it)))
        }
    }

    @Test
    fun localFileMetadataRoundTrips() {
        val values = listOf(
            ValueRef.LocalFile("payload.json"),
            ValueRef.LocalFile(" data/payload.json "),
            ValueRef.LocalFile("payload.json", "UTF-16", false),
            ValueRef.LocalFile(""),
            ValueRef.LocalFile("line\nbreak.json")
        )
        values.forEach { assertEquals(it, GuiValueRefCodec.parse(GuiValueRefCodec.toDisplayValue(it), true)) }
    }

    @Test
    fun unusualEnvironmentKeyRetainsItsSource() {
        val value = ValueRef.Env("TOKEN WITH SPACES")
        assertEquals(value, GuiValueRefCodec.parse(GuiValueRefCodec.toDisplayValue(value)))
    }

    @Test
    fun extendedReferenceTextCanRemainLiteral() {
        assertLiteralRoundTrip("""@ref:{"type":"env","key":"TOKEN"}""")
    }

    @Test
    fun malformedExtendedReferencesPreserveInput() {
        listOf("@ref:", " @ref:{broken} ", """@ref:{"type":"unknown"}""").forEach {
            assertEquals(ValueRef.Literal(it), GuiValueRefCodec.parse(it, true))
            assertEquals(it, GuiValueRefCodec.toDisplayValue(ValueRef.Literal(it)))
        }
    }

    private fun assertLiteralRoundTrip(value: String) {
        val literal = ValueRef.Literal(value)
        val display = GuiValueRefCodec.toDisplayValue(literal)
        assertEquals(literal, GuiValueRefCodec.parse(display), "Literal changed: $value")
        assertEquals(literal, GuiValueRefCodec.parse(display, true), "Body literal changed: $value")
    }
}
