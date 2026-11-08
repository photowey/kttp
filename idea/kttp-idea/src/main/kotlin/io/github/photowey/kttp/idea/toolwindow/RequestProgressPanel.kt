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

import com.intellij.openapi.Disposable
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.github.photowey.kttp.idea.execution.RequestExecutionMode
import java.awt.FlowLayout
import java.util.Locale
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.Timer

/**
 * Shows indeterminate request activity and elapsed wall time without guessing runtime phases.
 * Its EDT timer and animation stop on completion and are released with the owning editor.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/07
 */
internal class RequestProgressPanel : JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(3))), Disposable {
    private val spinner = AsyncProcessIcon("Request in progress", arrayOf(
        AllIcons.Process.Step_1, AllIcons.Process.Step_2, AllIcons.Process.Step_3, AllIcons.Process.Step_4,
        AllIcons.Process.Step_5, AllIcons.Process.Step_6, AllIcons.Process.Step_7, AllIcons.Process.Step_8
    ), AllIcons.Process.Step_passive)
    private val label = JLabel().apply {
        foreground = UIUtil.getContextHelpForeground()
        accessibleContext.accessibleName = "Request progress"
    }
    private val timer = Timer(200) { updateElapsed() }
    private var started = 0L
    private var activity = ""

    init {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        add(spinner)
        add(label)
        finish()
    }

    fun start(mode: RequestExecutionMode) {
        started = System.nanoTime()
        activity = if (mode == RequestExecutionMode.FOLLOW_SCRIPT) "Running script" else "Sending request"
        label.toolTipText = if (mode == RequestExecutionMode.FOLLOW_SCRIPT) {
            "Includes script startup, compilation when needed, execution and response processing. First execution can take longer."
        } else "Waiting for the HTTP request and response processing to finish."
        updateElapsed()
        isVisible = true
        spinner.isVisible = true
        label.isVisible = true
        spinner.resume()
        timer.start()
        revalidate()
    }

    fun cancelling() {
        activity = "Cancelling"
        updateElapsed()
    }

    fun finish() {
        timer.stop()
        spinner.suspend()
        spinner.isVisible = false
        label.isVisible = false
        isVisible = false
        revalidate()
    }

    override fun dispose() {
        finish()
        Disposer.dispose(spinner)
    }

    private fun updateElapsed() {
        val seconds = (System.nanoTime() - started).coerceAtLeast(0) / 1_000_000_000.0
        label.text = "$activity · ${String.format(Locale.ROOT, "%.1f", seconds)} s"
    }
}
