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

import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import io.github.photowey.kttp.core.context.execution.DeclarationCoordinates
import io.github.photowey.kttp.idea.lang.KttpFileType
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.text.isBlank

/**
 * Extension functions for KttpRunConfiguration.
 */

/**
 * Get script file as NIO Path.
 */
fun KttpRunConfiguration.getScriptFileNioPath(): Path? {
    val path = scriptPath ?: return null
    return Paths.get(path)
}

/**
 * Get script file as VirtualFile.
 */
fun KttpRunConfiguration.getScriptVirtualFile(): VirtualFile? {
    val path = scriptPath ?: return null
    val vf = LocalFileSystem.getInstance().findFileByPath(path) ?: return null
    return vf.takeIf { it.name.endsWith(".${KttpFileType.INSTANCE.defaultExtension}") }
}

/**
 * Check run strategy settings for validity.
 */
internal fun checkSettings(runStrategy: ScriptRunStrategy) {
    when (runStrategy) {
        is ScriptRunStrategy.SingleDeclaration -> {
            when (val coords = runStrategy.coordinates) {
                is DeclarationCoordinates.Number -> {
                    if (coords.number < 1) {
                        throw RuntimeConfigurationError("Request number can't be less than 1")
                    }
                }

                is DeclarationCoordinates.SourceLines -> {
                    throw RuntimeConfigurationError("Source coordinates are resolved at launch. Select a request number or name.")
                }

                is DeclarationCoordinates.Name -> {
                    if (coords.name.isBlank()) {
                        throw RuntimeConfigurationError("Request name can't be blank")
                    }
                }
            }
        }

        is ScriptRunStrategy.WholeFile -> {
            // Valid
        }
    }
}
