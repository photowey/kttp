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

package io.github.photowey.kttp.idea.importing.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.openapi.ui.ComboBox
import io.github.photowey.kttp.idea.env.KttpEnvironmentService
import io.github.photowey.kttp.idea.importing.ImportBundle
import io.github.photowey.kttp.idea.importing.ImportCommitResult
import io.github.photowey.kttp.idea.importing.ImportEnvironmentWriter
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * Offers an explicit imported-profile choice and applies it only after a successful import.
 * Actual committed names are used because existing profiles may require numeric suffixes.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/14
 */
internal class ImportEnvironmentSelection(project: Project) : JPanel(BorderLayout(0, JBUI.scale(6))) {
    private val service = KttpEnvironmentService.getInstance(project)
    internal val useImported = JBCheckBox("Use imported environment")
    internal val profiles = ComboBox<String>()
    private val hint = JTextArea("Optional Postman profiles appear after preview.", 2, 0).apply {
        isEditable = false; isFocusable = false; isOpaque = false; lineWrap = true; wrapStyleWord = true
        font = UIUtil.getLabelFont(); foreground = UIUtil.getContextHelpForeground()
    }
    private val choices = JPanel(BorderLayout(0, JBUI.scale(6))).apply {
        add(useImported, BorderLayout.NORTH)
        add(profiles, BorderLayout.CENTER)
        isVisible = false
    }
    private var automatic = false
    private var configured = false

    init {
        add(choices, BorderLayout.NORTH)
        add(hint, BorderLayout.CENTER)
        profiles.accessibleContext.accessibleName = "Imported environment to activate"
        useImported.toolTipText = "Applies only after a successful import; existing profiles are preserved."
        useImported.addActionListener { automatic = false; profiles.isEnabled = useImported.isSelected }
    }

    fun configure(bundle: ImportBundle) {
        val names = ImportEnvironmentWriter.profileNames(bundle)
        configured = true
        profiles.removeAllItems()
        profiles.addItem("Choose an environment…")
        names.forEach(profiles::addItem)
        automatic = names.size == 1 && service.selectedEnvironment == null
        useImported.isSelected = automatic
        profiles.selectedIndex = if (names.size == 1) 1 else 0
        profiles.isEnabled = useImported.isSelected
        choices.isVisible = names.isNotEmpty()
        hint.text = when {
            names.isEmpty() -> "No environment profiles to import."
            service.selectedEnvironment == null -> "Applied after import. Existing profiles are kept."
            else -> "Current: ${service.selectedEnvironment}. Kept unless you opt in."
        }
        hint.toolTipText = hint.text
        revalidate()
    }

    fun apply(result: ImportCommitResult) {
        val names = result.environmentProfiles
        if (!configured) {
            if (names.size == 1 && service.selectedEnvironment == null) service.setSelectedEnvironment(names.single())
            return
        }
        if (!useImported.isSelected || (automatic && service.selectedEnvironment != null)) return
        names.getOrNull(profiles.selectedIndex - 1)?.let(service::setSelectedEnvironment)
    }
}
