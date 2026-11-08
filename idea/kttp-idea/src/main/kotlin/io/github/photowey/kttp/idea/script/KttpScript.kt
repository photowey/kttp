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

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPsiElementPointer
import io.github.photowey.kttp.core.context.execution.DeclarationCoordinates
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * 包装脚本文件并从 Kotlin PSI 收集静态请求候选；候选序号不等同于动态运行时的注册坐标。
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/17
 */
class KttpScript(
    ktFile: KtFile
) {
    private val ktFilePointer: SmartPsiElementPointer<KtFile> =
        com.intellij.psi.SmartPointerManager.getInstance(ktFile.project).createSmartPsiElementPointer(ktFile)

    val project: Project = ktFile.project

    val ktFile: KtFile?
        get() = ktFilePointer.element?.takeIf { it.isValid }

    val virtualFile: VirtualFile?
        get() = ktFile?.virtualFile

    val nameWithoutExtension: String
        get() = virtualFile?.name?.substringBeforeLast(".") ?: "unknown"

    /** 按源文件顺序收集 PSI 中可注册的请求候选；不编译脚本，也不保证与动态运行时注册一一对应。 */
    fun findRequestDeclarations(): List<KttpRequestDeclaration> {
        val file = ktFile ?: return emptyList()
        val script = file.script ?: return emptyList()

        val declarations = mutableListOf<KttpRequestDeclaration>()

        // 遍历脚本内容
        script.blockExpression?.statements?.forEach { statement ->
            findRequestDeclarationsInElement(statement, declarations)
        }

        return declarations.filter { it.isRegistrable() }
    }

    private fun findRequestDeclarationsInElement(
        element: com.intellij.psi.PsiElement,
        result: MutableList<KttpRequestDeclaration>
    ) {
        when (element) {
            is KtCallExpression -> {
                element.toKttpRequestDeclaration()?.let {
                    if (it.isRegistrable()) result.add(it)
                }
                // 继续查找子元素（用于 useCase 等嵌套情况）
                element.valueArguments.forEach { arg ->
                    arg.getArgumentExpression()?.let { expr ->
                        findRequestDeclarationsInElement(expr, result)
                    }
                }
            }

            else -> {
                element.children.forEach { child ->
                    findRequestDeclarationsInElement(child, result)
                }
            }
        }
    }

    /** 按名称或一基序号查找静态请求候选；不处理源码行坐标，不能直接套用 CLI 的零基注册序号。 */
    fun getRequestDeclaration(coordinates: DeclarationCoordinates): KttpRequestDeclaration? {
        val declarations = findRequestDeclarations()

        return when (coordinates) {
            is DeclarationCoordinates.Name -> {
                declarations.find { it.getName() == coordinates.name }
            }

            is DeclarationCoordinates.Number -> {
                declarations.getOrNull(coordinates.number - 1)
            }

            else -> null
        }
    }

    /** 返回静态候选中的一基序号；元素失效返回 -1，元素有效但未找到时返回 0。 */
    fun getRequestDeclarationNumber(declaration: KttpRequestDeclaration): Int {
        val declarations = findRequestDeclarations()
        val element = declaration.element ?: return -1
        return declarations.indexOfFirst {
            it.element?.isEquivalentTo(element) == true
        } + 1
    }
}

/**
 * 将 VirtualFile 转换为 KttpScript
 */
fun VirtualFile.toKttpScript(project: Project): KttpScript? {
    if (!this.name.endsWith(".kttp.kts")) return null
    val psiFile = PsiManager.getInstance(project).findFile(this) as? KtFile ?: return null
    return KttpScript(psiFile)
}

/**
 * 将 KtFile 转换为 KttpScript
 */
fun KtFile.toKttpScript(): KttpScript? {
    if (!this.name.endsWith(".kttp.kts")) return null
    return KttpScript(this)
}

/**
 * 检查 VirtualFile 是否是 Kttp 脚本
 */
fun VirtualFile.isKttpFile(): Boolean = this.name.endsWith(".kttp.kts")

/**
 * 检查 KtFile 是否是 Kttp 脚本
 */
fun KtFile.isKttpFile(): Boolean = this.name.endsWith(".kttp.kts")
