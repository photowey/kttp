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

package io.github.photowey.kttp.idea.lang

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutor

/**
 * Substitutes Kttp file type → Kotlin language for parsing.
 * So .kttp.kts is parsed as Kotlin and RunLineMarkerContributor (Kotlin) + completions/inspections work.
 * File type stays Kttp (icon); language becomes Kotlin for PSI.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpLanguageSubstitutor : LanguageSubstitutor() {

    private val LOG = Logger.getInstance(KttpLanguageSubstitutor::class.java)

    override fun getLanguage(file: VirtualFile, project: Project): Language? {
        if (!file.name.endsWith("." + KttpFileType.INSTANCE.defaultExtension)) {
            return null
        }
        LOG.info("[KttpLanguageSubstitutor] Substituting language for: ${file.name}")
        val kotlinLanguage = Language.findLanguageByID("kotlin") ?: Language.findLanguageByID("Kotlin")
        LOG.info("[KttpLanguageSubstitutor] Returning Kotlin language: $kotlinLanguage")
        return kotlinLanguage
    }
}
