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

package io.github.photowey.kttp.idea.gui

import io.github.photowey.kttp.idea.script.toKttpScript
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Builds Kotlin PSI fixtures and extracts the first request projection for tests.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
object PsiScriptTestSupport {

    fun extractFirstProjection(scriptText: String): RequestProjection {
        var projection: RequestProjection? = null
        // Let the platform fixture own the application/project lifecycle and EDT dispatch.
        // A compiler-embeddable project uses relocated IntelliJ PSI and cannot be mixed
        // with the Kotlin plugin's unshaded PSI used by the production extractor.
        val fixture = object : LightPlatformTestCase() {
            fun extract() {
                runBare {
                    val file = KtPsiFactory(project, false).createFile("sample.kttp.kts", scriptText)
                    val declaration = requireNotNull(file.toKttpScript())
                        .findRequestDeclarations()
                        .first()
                    projection = RequestProjectionExtractor.extract(
                        declaration = declaration,
                        ktFile = file,
                        declarationIndex = 0,
                        sourceFilePath = "/tmp/sample.kttp.kts"
                    )
                }
            }
        }
        fixture.name = "testProjection"
        fixture.extract()
        return requireNotNull(projection)
    }
}
