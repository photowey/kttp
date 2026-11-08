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

package io.github.photowey.kttp.idea.run

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import io.github.photowey.kttp.idea.icons.KttpIcons

/**
 * Creates the template run configuration and options for Kttp scripts.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpRunConfigurationType : SimpleConfigurationType(
    "KttpRunConfigurationType",
    "Kttp",
    "Run a .kttp.kts script",
    NotNullLazyValue.lazy { KttpIcons.File }
) {

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        KttpRunConfiguration(project, this, "Kttp")

    override fun getOptionsClass(): Class<out RunConfigurationOptions> =
        KttpRunConfigurationOptions::class.java
}
