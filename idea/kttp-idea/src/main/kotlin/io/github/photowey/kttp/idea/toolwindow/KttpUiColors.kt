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

import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color

/**
 * Semantic accents only; surfaces and controls use the active LookAndFeel.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal object KttpUiColors {
    val success: Color = JBColor.namedColor("Label.successForeground", JBColor(0x247A38, 0x73BD79))
    val warning: Color = JBColor.namedColor("Label.warningForeground", JBColor(0x856007, 0xD6AF64))
    val error: Color = JBColor.namedColor("Label.errorForeground", JBColor(0xBC3030, 0xF08080))
    val info: Color = JBColor.namedColor("Link.activeForeground", JBColor(0x315FBD, 0x82A8F8))
    private val purple: Color = JBColor.namedColor("Kttp.method.postForeground", JBColor(0x7652A7, 0xBC9BE0))

    fun method(method: String): Color = when (method.uppercase()) {
        "GET" -> success
        "POST" -> purple
        "PUT" -> info
        "DELETE" -> error
        "PATCH" -> warning
        else -> UIUtil.getLabelForeground()
    }
}
