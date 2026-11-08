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

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import io.github.photowey.kttp.core.context.execution.DeclarationCoordinates
import io.github.photowey.kttp.idea.lang.KttpFileType
import io.github.photowey.kttp.idea.script.*
import org.jetbrains.kotlin.psi.KtFile

private val LOG = Logger.getInstance(KttpRunConfigurationProducer::class.java)

/**
 * Creates Kttp Run Configuration from context using LazyRunConfigurationProducer.
 * Sets sourceElement when caret is on a GET/POST line so the platform can show the Run icon in the gutter.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpRunConfigurationProducer : LazyRunConfigurationProducer<KttpRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory {
        return KttpRunConfigurationType()
    }

    override fun setupConfigurationFromContext(
        configuration: KttpRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        LOG.info("[KttpRunConfigurationProducer] setupConfigurationFromContext called")

        val location = context.location
        if (location == null) {
            LOG.info("[KttpRunConfigurationProducer] location is null")
            return false
        }

        val psiElement = location.psiElement
        LOG.info("[KttpRunConfigurationProducer] psiElement: ${psiElement.javaClass.simpleName}")

        // 检查是否在 Kttp 脚本中
        val virtualFile = PsiUtilCore.getVirtualFile(psiElement)
        if (virtualFile == null) {
            LOG.info("[KttpRunConfigurationProducer] virtualFile is null")
            return false
        }

        if (!virtualFile.name.endsWith(".${KttpFileType.INSTANCE.defaultExtension}")) {
            LOG.info("[KttpRunConfigurationProducer] not a kttp file: ${virtualFile.name}")
            return false
        }

        LOG.info("[KttpRunConfigurationProducer] processing kttp file: ${virtualFile.name}")

        // 获取 KtFile
        val ktFile = psiElement.containingFile as? KtFile
        if (ktFile == null) {
            LOG.info("[KttpRunConfigurationProducer] containingFile is not KtFile")
            return false
        }

        val kttpScript = ktFile.toKttpScript()
        if (kttpScript == null) {
            LOG.info("[KttpRunConfigurationProducer] failed to create KttpScript")
            return false
        }

        // 关键：找到光标位置的请求声明
        // 如果 psiElement 是整个文件，我们需要找到光标位置的请求
        val requestDeclaration = findRequestAtElement(psiElement, kttpScript)
        LOG.info("[KttpRunConfigurationProducer] requestDeclaration: $requestDeclaration, registrable: ${requestDeclaration?.isRegistrable()}")

        // 设置配置
        configuration.scriptPath = virtualFile.path
        configuration.selectedEnvironment = null
        configuration.runStrategy = calcRunStrategy(kttpScript, requestDeclaration)
        configuration.name = generateName(virtualFile.name, requestDeclaration)

        LOG.info("[KttpRunConfigurationProducer] configuration created: name=${configuration.name}, strategy=${configuration.runStrategy}")

        // 设置 sourceElement - 使用请求声明的元素或原始元素
        if (requestDeclaration != null && requestDeclaration.isRegistrable()) {
            val element = requestDeclaration.element
            if (element != null) {
                // 找到 HTTP 方法名的叶子元素
                val leafElement = findMethodNameLeaf(element)
                if (leafElement != null) {
                    sourceElement.set(leafElement)
                    LOG.info("[KttpRunConfigurationProducer] sourceElement set to: ${leafElement.javaClass.simpleName}, text=${leafElement.text}")
                } else {
                    sourceElement.set(element)
                    LOG.info("[KttpRunConfigurationProducer] sourceElement set to request element")
                }
            } else {
                LOG.info("[KttpRunConfigurationProducer] request declaration has no element")
            }
        } else {
            // 没有找到具体请求，使用整个文件
            sourceElement.set(psiElement)
            LOG.info("[KttpRunConfigurationProducer] sourceElement set to file element (WholeFile mode)")
        }

        return true
    }

    /**
     * 在元素位置找到请求声明
     */
    private fun findRequestAtElement(
        psiElement: PsiElement,
        kttpScript: KttpScript
    ): KttpRequestDeclaration? {
        // 尝试直接解析当前元素
        val declaration = psiElement.parseKttpRequestDeclaration()
        if (declaration != null && declaration.isRegistrable()) {
            LOG.info("[KttpRunConfigurationProducer] found request at current element")
            return declaration
        }

        // 如果直接解析失败，尝试向上查找
        var current: PsiElement? = psiElement.parent
        while (current != null) {
            val decl = current.parseKttpRequestDeclaration()
            if (decl != null && decl.isRegistrable()) {
                LOG.info("[KttpRunConfigurationProducer] found request at parent: ${current.javaClass.simpleName}")
                return decl
            }
            current = current.parent
        }

        // 如果没有找到具体请求，返回 null（将执行整个文件）
        LOG.info("[KttpRunConfigurationProducer] no request found, will run whole file")
        return null
    }

    /**
     * 找到 HTTP 方法名的叶子元素
     */
    private fun findMethodNameLeaf(callExpression: org.jetbrains.kotlin.psi.KtCallExpression): PsiElement? {
        val callee = callExpression.calleeExpression as? org.jetbrains.kotlin.psi.KtNameReferenceExpression
        return callee?.firstChild
    }

    override fun isConfigurationFromContext(
        configuration: KttpRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val virtualFile = context.location?.virtualFile
        if (virtualFile == null) {
            return false
        }

        if (!virtualFile.name.endsWith(".${KttpFileType.INSTANCE.defaultExtension}")) {
            return false
        }

        if (configuration.scriptPath != virtualFile.path) return false

        val psiElement = context.location?.psiElement ?: return false
        val ktFile = psiElement.containingFile as? KtFile ?: return false
        val script = ktFile.toKttpScript() ?: return false
        val declaration = findRequestAtElement(psiElement, script)
        return configuration.runStrategy == calcRunStrategy(script, declaration)
    }

    override fun shouldReplace(
        self: com.intellij.execution.actions.ConfigurationFromContext,
        other: com.intellij.execution.actions.ConfigurationFromContext
    ): Boolean {
        return true
    }

    /**
     * 计算运行策略
     */
    private fun calcRunStrategy(
        kttpScript: KttpScript,
        requestDeclaration: KttpRequestDeclaration?
    ): ScriptRunStrategy {
        if (requestDeclaration == null || !requestDeclaration.isRegistrable()) {
            return ScriptRunStrategy.WholeFile
        }

        val coordinates = getCoordinates(kttpScript, requestDeclaration)
        LOG.info("[KttpRunConfigurationProducer] calcRunStrategy: SingleDeclaration with coordinates=$coordinates")
        return ScriptRunStrategy.SingleDeclaration(coordinates)
    }

    /**
     * 获取请求声明的坐标
     */
    private fun getCoordinates(
        kttpScript: KttpScript,
        declaration: KttpRequestDeclaration
    ): DeclarationCoordinates {
        val name = declaration.getName()
        val nameArgument = declaration.element?.valueArguments?.firstOrNull {
            it.getArgumentName()?.asName?.asString() in setOf("name", "alias")
        }
        val literalName =
            (nameArgument?.getArgumentExpression() as? org.jetbrains.kotlin.psi.KtStringTemplateExpression)
                ?.entries?.singleOrNull() is org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
        // Expression text is a display label, not an evaluated runtime request name.
        if (!name.isNullOrBlank() && (nameArgument == null || literalName)) {
            return DeclarationCoordinates.Name(name)
        }

        val number = kttpScript.getRequestDeclarationNumber(declaration)
        return DeclarationCoordinates.Number(if (number > 0) number else 1)
    }

    /**
     * 生成配置名称
     */
    private fun generateName(fileName: String, declaration: KttpRequestDeclaration?): String {
        val baseName = fileName.substringBeforeLast(".")
        if (declaration == null) return baseName

        val requestName = declaration.getName()
        if (!requestName.isNullOrBlank()) {
            return "$baseName: $requestName"
        }

        val method = declaration.getHttpMethod()
        if (method != null) {
            return "$baseName: $method"
        }

        return baseName
    }
}
