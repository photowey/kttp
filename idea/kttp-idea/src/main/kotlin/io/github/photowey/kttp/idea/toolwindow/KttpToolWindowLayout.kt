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
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.util.ui.JBUI
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Applies a bounded initial docked width once per project, leaving subsequent IDE layout choices intact.
 *
 * @author photowey
 * @version 1.1.0
 * @since 2026/11/12
 */
internal object KttpToolWindowLayout {
    private const val INITIAL_WIDTH_APPLIED = "Kttp.Workspace.InitialWidthApplied"

    fun install(project: Project, toolWindow: ToolWindow, content: JComponent) {
        val settings = PropertiesComponent.getInstance(project)
        if (settings.getBoolean(INITIAL_WIDTH_APPLIED)) return
        lateinit var listener: HierarchyListener
        lateinit var resizeListener: ComponentAdapter
        fun applyWhenShowing() {
            if (project.isDisposed || !content.isShowing) return
            val rootWidth = SwingUtilities.getRootPane(content)?.width ?: return
            if (rootWidth <= 0 || toolWindow.component.width <= 0) return
            content.removeHierarchyListener(listener)
            content.removeComponentListener(resizeListener)
            settings.setValue(INITIAL_WIDTH_APPLIED, true)
            if (toolWindow.type != ToolWindowType.DOCKED ||
                toolWindow.anchor !in listOf(ToolWindowAnchor.LEFT, ToolWindowAnchor.RIGHT)) return
            val window = toolWindow as? ToolWindowEx ?: return
            val desiredWidth = minOf(JBUI.scale(1120), (rootWidth * 0.85).toInt())
            val extraWidth = desiredWidth - toolWindow.component.width
            if (extraWidth > 0) window.stretchWidth(extraWidth)
        }
        listener = HierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) applyWhenShowing()
        }
        resizeListener = object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) = applyWhenShowing()
        }
        content.addHierarchyListener(listener)
        content.addComponentListener(resizeListener)
        applyWhenShowing()
    }
}
