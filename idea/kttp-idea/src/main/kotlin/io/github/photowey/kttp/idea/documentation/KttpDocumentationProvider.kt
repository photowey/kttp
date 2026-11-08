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

package io.github.photowey.kttp.idea.documentation

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.PsiElement
import io.github.photowey.kttp.idea.lang.KttpFileType
import io.github.photowey.kttp.idea.substitutor.KttpLanguageSubstitutor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReferenceExpression
import java.io.File

/**
 * Quick Documentation for .kttp.kts delegates using the IDE process environment and filesystem.
 * `by env` looks up the property name in System.getenv, without consulting the selected Kttp environment.
 * `by localFile` checks a path relative to the script directory, without applying the runner's base-dir override.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpDocumentationProvider : DocumentationProvider {

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val el = element ?: originalElement ?: return null
        val file = el.containingFile ?: return null
        if (!file.name.endsWith("." + KttpFileType.INSTANCE.defaultExtension)) return null

        val property = when (el) {
            is KtProperty -> el
            else -> el.reference?.resolve() as? KtProperty ?: (el.parent as? KtProperty)
        } ?: return null

        val name = property.name ?: return null
        return when {
            KttpLanguageSubstitutor.isEnvDelegate(property) -> {
                val value = System.getenv(name)
                if (value != null) "by env <b>$name</b><br/>Value: <code>${escapeHtml(value.take(200))}</code>"
                else "by env <b>$name</b><br/><i>(not set)</i>"
            }

            KttpLanguageSubstitutor.isLocalFileDelegate(property) -> {
                val delegateExpr = property.delegateExpression as? KtCallExpression ?: return null
                val pathArg = KttpLanguageSubstitutor.getLocalFilePathArgument(delegateExpr) ?: return null
                val scriptDir = file.virtualFile?.parent?.path ?: return null
                val resolved = if (pathArg.startsWith("/")) pathArg else File(scriptDir, pathArg).absolutePath
                val exists = File(resolved).exists()
                "by localFile(\"$pathArg\")<br/>Resolved: <code>${escapeHtml(resolved)}</code><br/>${if (exists) "✓ exists" else "✗ not found"}"
            }

            else -> null
        }
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
