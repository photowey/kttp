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
import com.intellij.psi.PsiElementVisitor
import io.github.photowey.kttp.idea.lang.KttpFileType
import io.github.photowey.kttp.script.KttpScriptParser

/**
 * 对 .kttp.kts 文件做轻量检查：若无法解析出第一个请求（GET/POST/...("url")），在编辑器中标注警告。
 * 与 §6.7 错误定位高亮对应；完整编译错误依赖 §5.2 脚本编译管线。
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpScriptInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitFile(file: com.intellij.psi.PsiFile) {
                if (!file.name.endsWith("." + KttpFileType.INSTANCE.defaultExtension)) return
                if (KttpScriptParser.parseFirstRequest(file.text) != null) return
                holder.registerProblem(
                    file,
                    "No executable request found. Expected GET(\"url\"), POST(\"url\"), etc.",
                    com.intellij.codeInspection.ProblemHighlightType.WEAK_WARNING
                )
            }
        }
    }
}
