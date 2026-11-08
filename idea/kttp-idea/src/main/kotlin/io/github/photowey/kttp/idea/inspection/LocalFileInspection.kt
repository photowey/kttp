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

package io.github.photowey.kttp.idea.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import io.github.photowey.kttp.idea.lang.KttpFileType
import io.github.photowey.kttp.idea.substitutor.KttpLanguageSubstitutor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

/**
 * 对 .kttp.kts 中 localFile("path") 做检查：若路径指向的文件不存在，显示弱警告。
 * 相对路径相对于脚本文件所在目录解析。
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class LocalFileInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is KtCallExpression &&
                    element.calleeExpression?.text == KttpLanguageSubstitutor.LOCAL_FILE_FUNCTION_NAME
                ) {
                    val file = element.containingFile as? KtFile ?: return
                    if (!file.name.endsWith("." + KttpFileType.INSTANCE.defaultExtension)) return
                    val scriptDir = file.virtualFile?.parent?.path ?: return
                    val pathArg = KttpLanguageSubstitutor.getLocalFilePathArgument(element) ?: return
                    val resolved = if (pathArg.startsWith("/")) pathArg else File(scriptDir, pathArg).absolutePath
                    if (!File(resolved).exists()) {
                        holder.registerProblem(
                            element,
                            "File does not exist: $pathArg",
                            com.intellij.codeInspection.ProblemHighlightType.WEAK_WARNING
                        )
                    }
                }
                element.acceptChildren(this)
            }
        }
    }
}
