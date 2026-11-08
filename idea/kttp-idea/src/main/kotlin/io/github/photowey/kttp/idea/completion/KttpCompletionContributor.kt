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

package io.github.photowey.kttp.idea.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import io.github.photowey.kttp.idea.lang.KttpFileType
import io.github.photowey.kttp.idea.substitutor.KttpLanguageSubstitutor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import java.io.File

/**
 * Code completion for .kttp.kts: HTTP methods, DSL keywords, variable names from by env / by localFile,
 * and relative path completion inside localFile("...").
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    if (!parameters.originalFile.name.endsWith(".${KttpFileType.INSTANCE.defaultExtension}")) return
                    val prefix = result.prefixMatcher.prefix
                    if (prefix.isEmpty()) return
                    val lower = prefix.lowercase()

                    addLocalFilePathCompletions(parameters, result)

                    (HTTP_METHODS + DSL_KEYWORDS)
                        .filter { it.lowercase().startsWith(lower) }
                        .forEach { word ->
                            result.addElement(
                                LookupElementBuilder.create(word)
                                    .withBoldness(word in HTTP_METHODS)
                            )
                        }
                    (parameters.originalFile as? KtFile)?.let { ktFile ->
                        KttpLanguageSubstitutor.collectDelegateVariableNames(ktFile)
                            .filter { it.lowercase().startsWith(lower) }
                            .forEach { name ->
                                result.addElement(
                                    LookupElementBuilder.create(name)
                                        .withTailText(" (env/localFile)", true)
                                )
                            }
                    }
                }
            }
        )
    }

    private fun addLocalFilePathCompletions(parameters: CompletionParameters, result: CompletionResultSet) {
        val el = parameters.originalFile.findElementAt(parameters.offset) ?: return
        val call = el.parentOfType<org.jetbrains.kotlin.psi.KtCallExpression>() ?: return
        if ((call.calleeExpression as? org.jetbrains.kotlin.psi.KtReferenceExpression)?.text != KttpLanguageSubstitutor.LOCAL_FILE_FUNCTION_NAME) return
        val argExpr = call.valueArguments.firstOrNull()?.getArgumentExpression() ?: return
        if (argExpr !is KtStringTemplateExpression) return
        val pathPrefix = argExpr.text.trim('"').replace("\\\"", "\"")
        val scriptDir = parameters.originalFile.virtualFile?.parent?.path ?: return
        val baseDir: File
        val segmentPrefix: String
        when {
            pathPrefix.isEmpty() -> {
                baseDir = File(scriptDir); segmentPrefix = ""
            }

            pathPrefix.endsWith('/') -> {
                baseDir = File(scriptDir, pathPrefix.dropLast(1)); segmentPrefix = ""
            }

            else -> {
                baseDir = File(scriptDir, pathPrefix).parentFile?.takeIf { it.exists() } ?: File(scriptDir)
                segmentPrefix = pathPrefix.substringAfterLast('/')
            }
        }
        if (!baseDir.isDirectory) return
        baseDir.listFiles()?.orEmpty()
            ?.filter { f -> f.name.startsWith(segmentPrefix, ignoreCase = true) }
            ?.sortedBy { f -> !f.isDirectory }
            ?.forEach { f ->
                val insert = f.name + if (f.isDirectory) "/" else ""
                result.addElement(
                    LookupElementBuilder.create(insert)
                        .withTailText(if (f.isDirectory) " (dir)" else " (file)", true)
                )
            }
    }

    /**
     * Lists HTTP methods and DSL keywords offered by Kttp completion.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    private companion object {
        private val HTTP_METHODS = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
        private val DSL_KEYWORDS = listOf("then", "by", "body", "headers", "query", "path")
    }
}
