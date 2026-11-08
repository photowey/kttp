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

package io.github.photowey.kttp.idea.icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Loads Kttp file, environment, tool-window, and HTTP-method icons.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
object KttpIcons {
    @JvmField
    val File: Icon = IconLoader.getIcon("/icons/kttp.svg", KttpIcons::class.java)

    @JvmField
    val EnvFile: Icon = IconLoader.getIcon("/icons/kttp.svg", KttpIcons::class.java)

    @JvmField
    val ToolWindow: Icon = IconLoader.getIcon("/icons/kttpToolWindow.svg", KttpIcons::class.java)

    /** Icons for HTTP methods (structure view, completion). Use File until method-specific SVGs are added. */
    @JvmField
    val MethodGet: Icon = File
    @JvmField
    val MethodPost: Icon = File
    @JvmField
    val MethodPut: Icon = File
    @JvmField
    val MethodDelete: Icon = File
    @JvmField
    val MethodPatch: Icon = File
    @JvmField
    val MethodHead: Icon = File
    @JvmField
    val MethodOptions: Icon = File

    /** Returns method-specific icon for structure view / line marker. */
    @JvmStatic
    fun iconForMethod(method: String): Icon = when (method.uppercase()) {
        "GET" -> MethodGet
        "POST" -> MethodPost
        "PUT" -> MethodPut
        "DELETE" -> MethodDelete
        "PATCH" -> MethodPatch
        "HEAD" -> MethodHead
        "OPTIONS" -> MethodOptions
        else -> File
    }
}
