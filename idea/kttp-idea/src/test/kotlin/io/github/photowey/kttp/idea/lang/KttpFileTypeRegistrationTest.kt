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

package io.github.photowey.kttp.idea.lang

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.psi.LanguageSubstitutors
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import io.github.photowey.kttp.idea.gui.GuiMode
import io.github.photowey.kttp.idea.gui.RequestProjectionExtractor
import io.github.photowey.kttp.idea.script.toKttpScript
import org.jetbrains.kotlin.psi.KtFile
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * §11.1 文件类型注册单元测试：验收 Kttp 文件类型与扩展名注册。
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpFileTypeRegistrationTest : BasePlatformTestCase() {

    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()

    fun testKttpLanguageHasIdKttp() {
        assertEquals("Kttp", KttpLanguage.id)
    }

    fun testKttpFileTypeDefaultExtension() {
        assertEquals("kttp.kts", KttpFileType.INSTANCE.defaultExtension)
    }

    fun testFileTypeManagerAssociatesExtensionWithKttpFileType() {
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName("script.kttp.kts")
        assertEquals(KttpFileType.INSTANCE, fileType)
    }

    fun testPluginDeclaresKttpScriptFilenameMatcher() {
        val bean = FileTypeManagerImpl.EP_NAME.extensionList.single {
            it.implementationClass == KttpFileType::class.java.name
        }
        assertEquals("*.kttp.kts", bean.patterns)
    }

    fun testPluginRegistersKotlinLanguageSubstitution() {
        val substitutors = LanguageSubstitutors.getInstance().forKey(KttpLanguage)
        assertTrue(substitutors.any { it is KttpLanguageSubstitutor })
    }

    fun testPhysicalKttpScriptHasKotlinPsiAndRequestProjection() {
        val file = myFixture.tempDirFixture.createFile(
            "physical.kttp.kts",
            "GET(\"https://example.com/users\") { query(\"page\", \"1\") }\n"
        )
        assertTrue(file.isInLocalFileSystem)
        assertEquals(KttpFileType.INSTANCE, file.fileType)
        val psiFile = assertIs<KtFile>(PsiManager.getInstance(project).findFile(file))
        assertTrue(psiFile.isPhysical)
        val script = psiFile.toKttpScript() ?: error("Expected a Kttp script for ${file.path}")
        val declaration = script.findRequestDeclarations().single()
        val projection = RequestProjectionExtractor.extract(declaration, psiFile, 0, file.path)
        assertEquals(GuiMode.GUI_READY, projection.mode)
        assertEquals("https://example.com/users", declaration.getUrl())
    }
}
