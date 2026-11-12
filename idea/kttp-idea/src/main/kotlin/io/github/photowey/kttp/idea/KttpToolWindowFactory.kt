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

package io.github.photowey.kttp.idea

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import io.github.photowey.kttp.idea.toolwindow.KttpToolWindowPanel
import io.github.photowey.kttp.idea.toolwindow.KttpToolWindowLayout
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Creates the Sources/Responses sidebar, request composer, and result tabs for the Kttp tool window.
 * Initialization failures are logged and shown in a fallback panel.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = try {
            val panel = KttpToolWindowPanel(project)
            ContentFactory.getInstance().createContent(panel, "", false)
        } catch (e: Throwable) {
            LOG.warn("Kttp tool window content creation failed", e)
            val fallback = JPanel(BorderLayout()).apply {
                add(JLabel("Kttp (loading…)", SwingConstants.CENTER), BorderLayout.CENTER)
            }
            ContentFactory.getInstance().createContent(fallback, "", false)
        }
        toolWindow.contentManager.addContent(content)
        if (content.component is KttpToolWindowPanel) {
            KttpToolWindowLayout.install(project, toolWindow, content.component)
        }
    }

    /**
     * Holds the logger used when creating the Kttp tool window.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    companion object {
        private val LOG = Logger.getInstance(KttpToolWindowFactory::class.java)
    }
}
