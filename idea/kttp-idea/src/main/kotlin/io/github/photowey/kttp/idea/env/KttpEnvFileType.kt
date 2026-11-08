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

package io.github.photowey.kttp.idea.env

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.json.JsonLanguage
import io.github.photowey.kttp.idea.icons.KttpIcons
import javax.swing.Icon

/**
 * File type for kttp.env.json - environment variables configuration.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/16
 */
class KttpEnvFileType private constructor() : LanguageFileType(JsonLanguage.INSTANCE) {

    override fun getName(): String = "Kttp Environment"
    override fun getDescription(): String = "Kttp environment variables configuration"
    override fun getDefaultExtension(): String = "kttp.env.json"
    override fun getIcon(): Icon = KttpIcons.EnvFile

    /**
     * Exposes the environment file type instance and its conventional filename.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/16
     */
    companion object {
        val INSTANCE = KttpEnvFileType()
        const val FILE_NAME = "kttp.env.json"
    }
}
