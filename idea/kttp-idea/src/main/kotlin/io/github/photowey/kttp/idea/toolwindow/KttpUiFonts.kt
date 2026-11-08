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

import com.intellij.util.ui.JBUI
import java.awt.Font
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.plaf.FontUIResource

/** Keep emphasis replaceable by the look and feel when the IDE font changes. */
internal fun JLabel.useBoldUiFont() {
    fun refresh() {
        if (!font.isBold) font = FontUIResource(font.deriveFont(Font.BOLD))
    }
    addPropertyChangeListener("font") { refresh() }
    refresh()
}

/** Swing does not recalculate a JTable's explicitly configured row height after a font change. */
internal fun JTable.fitRowHeightToFont() {
    fun refresh() {
        rowHeight = getFontMetrics(font).height + JBUI.scale(8)
    }
    addPropertyChangeListener("font") { refresh() }
    addPropertyChangeListener("UI") { refresh() }
    refresh()
}
