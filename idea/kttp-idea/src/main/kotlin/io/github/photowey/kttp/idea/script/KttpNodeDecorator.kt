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

package io.github.photowey.kttp.idea.script

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.vfs.VirtualFile
import io.github.photowey.kttp.idea.icons.KttpIcons
import io.github.photowey.kttp.idea.lang.KttpFileType

/**
 * Decorates .kttp.kts nodes in Project view with Kttp icon.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpNodeDecorator : ProjectViewNodeDecorator {

    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val file = (node.value as? VirtualFile) ?: return
        if (file.name.endsWith("." + KttpFileType.INSTANCE.defaultExtension)) {
            data.setIcon(KttpIcons.File)
        }
    }
}
