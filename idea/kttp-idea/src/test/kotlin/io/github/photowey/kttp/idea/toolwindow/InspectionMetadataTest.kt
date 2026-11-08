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

package io.github.photowey.kttp.idea.toolwindow

import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards required inspection metadata so opening script inspections cannot raise IDE error notifications.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/07
 */
class InspectionMetadataTest {
    @Test
    fun everyKttpInspectionHasAnExplicitGroupName() {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream("META-INF/plugin.xml"))
        val document = stream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        val inspections = document.getElementsByTagName("localInspection")
        val kttp = (0 until inspections.length).map { inspections.item(it) as org.w3c.dom.Element }
            .filter { it.getAttribute("shortName").startsWith("Kttp") }
        assertEquals(3, kttp.size)
        kttp.forEach { inspection ->
            assertTrue(inspection.getAttribute("groupName").isNotBlank(), inspection.getAttribute("shortName"))
        }
    }
}
