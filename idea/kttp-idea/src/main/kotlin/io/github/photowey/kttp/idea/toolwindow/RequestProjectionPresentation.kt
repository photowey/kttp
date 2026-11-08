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

import com.intellij.util.ui.UIUtil
import io.github.photowey.kttp.idea.gui.GuiMode
import java.awt.Color

/**
 * Contains a capability badge's text, theme colors, and explanatory tooltip.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
data class RequestProjectionBadge(
    val text: String,
    val background: Color,
    val foreground: Color,
    val tooltip: String
)

/**
 * Counts the request capability categories found in a script file.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
data class FileProjectionSummary(
    val guiReadyCount: Int = 0,
    val sendOnlyCount: Int = 0,
    val scriptOnlyCount: Int = 0
) {
    val total: Int get() = guiReadyCount + sendOnlyCount + scriptOnlyCount
}

/**
 * Defines the sidebar filters for script projection capabilities.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
enum class FileProjectionFilter(val label: String, val description: String) {
    ALL("All scripts", "Show every script file; all categories can use Follow Script."),
    HAS_GUI(
        "With full projections",
        "Show scripts with at least one Quick Request projection without known unsupported or omitted source elements."
    ),
    HAS_SEND(
        "With HTTP-only projections",
        "Show scripts with at least one sendable Quick Request projection that skips script processing."
    ),
    ONLY_SCRIPT(
        "Script required",
        "Show scripts whose requests all require Follow Script rather than Quick Request."
    ),
    MIXED("Mixed capabilities", "Show scripts containing different projection categories, including complete and HTTP-only projections.");

    override fun toString(): String = label
}

/**
 * Formats capability badges, summarizes files, and applies sidebar projection filters.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
object RequestProjectionPresentation {

    private const val QUICK_AVAILABLE = "Quick available"
    private const val SCRIPT_REQUIRED = "Script required"

    fun badge(mode: GuiMode): RequestProjectionBadge {
        return when (mode) {
            GuiMode.GUI_READY -> RequestProjectionBadge(
                text = QUICK_AVAILABLE,
                background = UIUtil.getPanelBackground(),
                foreground = KttpUiColors.success,
                tooltip = "Quick Request can send the projected HTTP data. Imported requests are read-only; " +
                    "use Detach to edit a copy. Select Follow Script to preserve the source script's behavior. " +
                    "This label describes support, not the selected execution mode."
            )

            GuiMode.SEND_ONLY -> RequestProjectionBadge(
                text = QUICK_AVAILABLE,
                background = UIUtil.getPanelBackground(),
                foreground = KttpUiColors.success,
                tooltip = "Quick Request can send the projected HTTP data but skips script processing. " +
                    "Imported requests are read-only; use Detach to edit a copy. Select Follow Script to preserve " +
                    "the source script's behavior. This label describes support, not the selected execution mode."
            )

            GuiMode.SCRIPT_ONLY -> RequestProjectionBadge(
                text = SCRIPT_REQUIRED,
                background = UIUtil.getPanelBackground(),
                foreground = UIUtil.getLabelForeground(),
                tooltip = "Quick Request cannot fully represent this request. Select Follow Script in the composer " +
                    "to execute it. Edit the Kotlin source with Open Script; Detach is unavailable. " +
                    "This label describes support, not the selected execution mode."
            )
        }
    }

    fun summary(modes: List<GuiMode>): FileProjectionSummary {
        return FileProjectionSummary(
            guiReadyCount = modes.count { it == GuiMode.GUI_READY },
            sendOnlyCount = modes.count { it == GuiMode.SEND_ONLY },
            scriptOnlyCount = modes.count { it == GuiMode.SCRIPT_ONLY }
        )
    }

    fun fileBadge(summary: FileProjectionSummary): RequestProjectionBadge {
        val text = "${summary.total} ${if (summary.total == 1) "request" else "requests"}"
        val tooltip = buildString {
            append("File summary")
            append("\n$QUICK_AVAILABLE: ").append(summary.guiReadyCount + summary.sendOnlyCount)
            append("\n$SCRIPT_REQUIRED: ").append(summary.scriptOnlyCount)
            append("\nExecution mode is selected in the composer.")
        }
        return RequestProjectionBadge(text, UIUtil.getPanelBackground(), UIUtil.getContextHelpForeground(), tooltip)
    }

    /** Filters file-level projection capabilities; this does not select the runtime execution mode of a request. */
    fun matchesFilter(summary: FileProjectionSummary, filter: FileProjectionFilter): Boolean {
        return when (filter) {
            FileProjectionFilter.ALL -> true
            FileProjectionFilter.HAS_GUI -> summary.guiReadyCount > 0
            FileProjectionFilter.HAS_SEND -> summary.sendOnlyCount > 0
            FileProjectionFilter.ONLY_SCRIPT -> summary.scriptOnlyCount > 0 && summary.guiReadyCount == 0 && summary.sendOnlyCount == 0
            FileProjectionFilter.MIXED -> listOf(
                summary.guiReadyCount,
                summary.sendOnlyCount,
                summary.scriptOnlyCount
            ).count { it > 0 } > 1
        }
    }

    fun sortWeight(summary: FileProjectionSummary): Int {
        return when {
            summary.guiReadyCount > 0 -> 0
            summary.sendOnlyCount > 0 -> 1
            summary.scriptOnlyCount > 0 -> 2
            else -> 3
        }
    }
}
