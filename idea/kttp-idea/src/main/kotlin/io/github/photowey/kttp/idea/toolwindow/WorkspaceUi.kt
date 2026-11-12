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

import com.intellij.ide.util.PropertiesComponent
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Shared workspace surfaces and discoverable dividers, using active IDE theme colors and scaled spacing.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/07
 */
internal object WorkspaceUi {
    fun surface(content: JComponent, padding: Int = 8): JPanel = WorkspaceSurface().apply {
        border = JBUI.Borders.empty(padding)
        minimumSize = Dimension(0, 0)
        add(content, BorderLayout.CENTER)
    }

    /** Proportions survive reopening; editor preferred/minimum sizes never prevent dragging. */
    fun split(
        vertical: Boolean, key: String, proportion: Float, first: JComponent, second: JComponent,
        initialFirstWidth: Int? = null
    ): JBSplitter =
        object : JBSplitter(vertical, key, proportion) {
            private var initialWidthPending = initialFirstWidth != null
            private var applyingDefaultWidth = false

            override fun loadProportion() {
                if (initialFirstWidth == null || PropertiesComponent.getInstance().isValueSet(key)) {
                    initialWidthPending = false
                    super.loadProportion()
                }
            }

            override fun saveProportion() {
                if (!applyingDefaultWidth) {
                    initialWidthPending = false
                    super.saveProportion()
                }
            }

            override fun doLayout() {
                if (initialWidthPending && width > dividerWidth) {
                    if (PropertiesComponent.getInstance().isValueSet(key)) {
                        loadProportion()
                    } else {
                        // Keep a useful initial width while the tool window opens; do not save automatic ratios.
                        applyingDefaultWidth = true
                        try {
                            setProportion((JBUI.scale(requireNotNull(initialFirstWidth)).toFloat() /
                                (width - dividerWidth)).coerceIn(0.22f, 0.38f))
                        } finally {
                            applyingDefaultWidth = false
                        }
                    }
                }
                super.doLayout()
            }

            override fun paintChildren(g: Graphics) {
                super.paintChildren(g)
                if (!divider.isVisible) return
                val graphics = g.create() as Graphics2D
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    graphics.color = UIUtil.getContextHelpForeground()
                    val centerX = divider.x + divider.width / 2
                    val centerY = divider.y + divider.height / 2
                    val dot = JBUI.scale(2)
                    for (offset in -1..1) {
                        val x = centerX + if (isVertical) offset * JBUI.scale(5) else 0
                        val y = centerY + if (isVertical) 0 else offset * JBUI.scale(5)
                        graphics.fillOval(x - dot / 2, y - dot / 2, dot, dot)
                    }
                } finally {
                    graphics.dispose()
                }
            }
        }.apply {
            firstComponent = first
            secondComponent = second
            minimumSize = Dimension(0, 0)
            dividerWidth = JBUI.scale(9)
            setHonorComponentsMinimumSize(false)
            setAllowSwitchOrientationByMouseClick(false)
            isOpaque = false
            divider.isOpaque = false
            divider.toolTipText = if (vertical) "Drag to resize height" else "Drag to resize width"
            divider.accessibleContext.accessibleName = divider.toolTipText
        }
}

/**
 * A restrained rounded container; children sit inside its outline so theme backgrounds cannot cover the corners.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/07
 */
private class WorkspaceSurface : JPanel(BorderLayout()) {
    init {
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val graphics = g.create() as Graphics2D
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = JBUI.scale(16)
            graphics.color = UIUtil.getPanelBackground()
            graphics.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
            graphics.color = JBColor.border()
            graphics.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
        } finally {
            graphics.dispose()
        }
    }
}
