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

import io.github.photowey.kttp.idea.gui.DraftOwnership
import io.github.photowey.kttp.idea.gui.GuiMode
import org.junit.Test
import java.awt.Color
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.plaf.ColorUIResource
import kotlin.test.assertEquals

/**
 * Verifies composer surfaces and controls follow theme changes.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class ComposerThemeTest {
    @Test
    fun composerSurfacesAndControlsFollowThemeChanges() {
        SwingUtilities.invokeAndWait {
            val keys = listOf(
                "Panel.background", "Label.foreground", "TextArea.background", "TextArea.foreground",
                "Button.background", "Button.foreground"
            )
            val originals = keys.associateWith(UIManager::get)
            try {
                val header = ComposerHeaderPanel()
                val bridge = ComposerBridgePanel()
                for (background in listOf(Color(0x242830), Color(0xF4F0E8), Color(0x27352D))) {
                    val foreground = if (background.red < 128) Color(0xE6E9EF) else Color(0x252830)
                    keys.forEach {
                        UIManager.put(
                            it,
                            ColorUIResource(if (it.endsWith("background")) background else foreground)
                        )
                    }
                    SwingUtilities.updateComponentTreeUI(header)
                    SwingUtilities.updateComponentTreeUI(bridge)
                    header.updateModeBadge(DraftOwnership.DETACHED, GuiMode.GUI_READY, false)
                    assertEquals(
                        JPanel().background.rgb,
                        header.background.rgb,
                        "Composer surface must follow Panel.background"
                    )
                    assertEquals(
                        JPanel().background.rgb,
                        bridge.background.rgb,
                        "Bridge surface must follow Panel.background"
                    )
                    assertEquals(
                        JButton().background.rgb,
                        header.saveDraftButton.background.rgb,
                        "Buttons must use native theme styling"
                    )
                    assertEquals(JButton().foreground.rgb, header.saveDraftButton.foreground.rgb)
                    bridge.components.filterIsInstance<JPanel>().forEach { section ->
                        assertEquals(
                            JPanel().background.rgb,
                            section.background.rgb,
                            "Bridge sections must follow the theme"
                        )
                    }
                    descendants(bridge).filterIsInstance<JTextArea>().forEach { area ->
                        assertEquals(
                            JTextArea().background.rgb,
                            area.background.rgb,
                            "Bridge text must use the theme background"
                        )
                        assertEquals(JTextArea().foreground.rgb, area.foreground.rgb)
                    }
                }
            } finally {
                originals.forEach { (key, value) -> UIManager.put(key, value) }
            }
        }
    }

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
    }
}
