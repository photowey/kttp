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

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import io.github.photowey.kttp.idea.lang.KttpFileType
import io.github.photowey.kttp.idea.script.parseKttpRequestDeclaration

private val LOG = Logger.getInstance(KttpRunLineMarkerContributor::class.java)

/**
 * Run line marker: shows Run icon on the HTTP method name (GET/POST/...) of a request.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpRunLineMarkerContributor : RunLineMarkerContributor() {

    override fun getInfo(element: PsiElement): Info? {
        // Must be a leaf element (identifier token like "GET", "POST")
        if (element !is LeafPsiElement) {
            return null
        }

        val file = element.containingFile
        if (file == null) {
            return null
        }

        // Check file extension
        if (!file.name.endsWith("." + KttpFileType.INSTANCE.defaultExtension)) {
            return null
        }

        // 使用新的请求声明解析
        val requestDeclaration = element.parseKttpRequestDeclaration()
        if (requestDeclaration == null) {
            return null
        }

        // 检查是否是可注册的请求
        if (!requestDeclaration.isRegistrable()) {
            return null
        }

        val methodName = requestDeclaration.getHttpMethod()
        LOG.debug("[KttpRunLineMarker] Found registrable request: $methodName at ${file.name}:${element.textOffset}")

        // 返回 Info，使用 ExecutorAction 会触发 RunConfigurationProducer
        return Info(
            AllIcons.Actions.Execute,
            ExecutorAction.getActions(0),
            { "Run $methodName request" }
        )
    }
}
