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

package io.github.photowey.kttp.idea.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import io.github.photowey.kttp.core.enums.HttpMethod
import io.github.photowey.kttp.core.resource.ResourceResolver
import io.github.photowey.kttp.idea.env.KttpEnvironmentService
import io.github.photowey.kttp.idea.gui.*
import io.github.photowey.kttp.idea.execution.RequestExecutionMode
import io.github.photowey.kttp.idea.script.BodySource
import io.github.photowey.kttp.idea.script.KttpRequestDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtScript
import com.intellij.psi.PsiElement
import io.github.photowey.kttp.idea.script.toKttpScript
import java.security.MessageDigest
import java.nio.charset.StandardCharsets

/**
 * Loads a script declaration into an imported GUI draft while retaining projection limits and source identity.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal class RequestEditorProjectionLoader(private val project: Project) {
    fun load(declaration: KttpRequestDeclaration, file: VirtualFile?, index: Int?): GuiDraft =
        ApplicationManager.getApplication().runReadAction(Computable {
            loadUnderReadAction(declaration, file, index)
        })

    private fun loadUnderReadAction(declaration: KttpRequestDeclaration, file: VirtualFile?, index: Int?): GuiDraft {
        val ktFile = file?.let { PsiManager.getInstance(project).findFile(it) as? KtFile }
        val projection = RequestProjectionExtractor.extract(declaration, ktFile, index, file?.path)
        return GuiDraft(
            id = importedIdentity(declaration, file, ktFile),
            title = declaration.getName() ?: declaration.presentableText() ?: declaration.getHttpMethod()
            ?: "Imported Request",
            ownership = DraftOwnership.SCRIPT_IMPORTED,
            executionMode = RequestExecutionMode.FOLLOW_SCRIPT,
            request = projection.spec ?: fallback(declaration, file, index, ktFile),
            bridge = ScriptBridgeState(
                sourceScriptPath = file?.path, mode = projection.mode,
                gaps = projection.gaps.ifEmpty {
                    listOf(
                        SemanticGap(
                            GapKind.PARTIALLY_PROJECTED,
                            message = "Imported from script. Quick Request sends the projected HTTP request; Follow Script evaluates the source script."
                        )
                    )
                })
        )
    }

    /** Keep mode preferences with a request when unrelated declarations are inserted before it. */
    private fun importedIdentity(selected: KttpRequestDeclaration, file: VirtualFile?, ktFile: KtFile?): String {
        val declarations = ktFile?.toKttpScript()?.findRequestDeclarations().orEmpty()
        val uniqueName = selected.getName()?.takeIf { name -> declarations.count { it.getName() == name } == 1 }
        fun source(request: KttpRequestDeclaration): String {
            var statement: PsiElement = request.element ?: return ""
            // Keep the entire enclosing statement, regardless of dot/infix/receiver/wrapper syntax.
            while (true) {
                val parent = statement.parent ?: break
                if (parent is KtBlockExpression || parent is KtScript || parent is KtFile) break
                statement = parent
            }
            return statement.text
        }

        val selectedSource = source(selected)
        val duplicates = declarations.count { source(it) == selectedSource }
        // Identical statements cannot be distinguished persistently after edits; invalidate conservatively.
        val context = if (uniqueName == null && duplicates > 1) ktFile?.text.orEmpty() else ""
        val identity = uniqueName?.let { "name:$it" } ?: "source:$selectedSource;context:$context"
        val occurrence = if (uniqueName != null) 0 else declarations.takeWhile { it.element != selected.element }
            .count { source(it) == selectedSource }
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "import:${file?.path ?: "memory"}:$hash:$occurrence"
    }

    private fun fallback(
        declaration: KttpRequestDeclaration,
        file: VirtualFile?,
        index: Int?,
        ktFile: KtFile?
    ): RequestSpec {
        val signature = declaration.element?.let { ProjectionSyntax.extractRequestSignature(it, declaration) }
        return RequestSpec(
            identity = RequestIdentity(declaration.getName(), file?.path, index),
            // A script-only placeholder never reaches Quick Request or script generation.
            method = signature?.method ?: HttpMethod.GET,
            target = RequestTarget(
                signature?.urlRef ?: ValueRef.OpaqueScript(declaration.getUrl().orEmpty()),
                queryParams = declaration.getQueryParams()
                    .map { (key, value) -> NameValueRef(key, ValueRef.Literal(value)) }),
            headers = declaration.getHeaders().map { (key, value) -> HeaderSpec(key, ValueRef.Literal(value)) },
            body = fallbackBody(declaration, file, ktFile)
        )
    }

    private fun fallbackBody(declaration: KttpRequestDeclaration, file: VirtualFile?, ktFile: KtFile?): BodySpec {
        val baseDir = project.basePath ?: file?.parent?.path
        val content = when (val source = declaration.getBodySource(ktFile)) {
            is BodySource.Literal -> source.content
            is BodySource.LocalFile -> readLocalFile(source.path, baseDir)
            is BodySource.Env -> KttpEnvironmentService.getInstance(project)
                .getEnvironmentVariables(file)[source.varName]?.takeIf(String::isNotBlank)

            BodySource.None -> null
        } ?: return BodySpec.None
        return when {
            content.trimStart().startsWith("{") || content.trimStart()
                .startsWith("[") -> BodySpec.Json(ValueRef.Literal(content))

            content.contains("=") -> {
                val fields = content.split("&").mapNotNull {
                    val parts = it.split("=", limit = 2)
                    if (parts.size == 2) NameValueRef(parts[0], ValueRef.Literal(parts[1])) else null
                }
                if (fields.isEmpty()) BodySpec.Raw(ValueRef.Literal(content)) else BodySpec.FormUrlEncoded(fields)
            }

            else -> BodySpec.Raw(ValueRef.Literal(content))
        }
    }

    private fun readLocalFile(path: String, baseDir: String?): String? = try {
        ResourceResolver(baseDir).resolve(path).let { if (it.exists()) it.readText(StandardCharsets.UTF_8) else null }
    } catch (e: Exception) {
        Logger.getInstance(RequestEditorProjectionLoader::class.java).warn("Failed to read localFile: $path", e)
        null
    }
}
