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

package io.github.photowey.kttp.idea.path

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that a completed save is immediately readable outside VFS and retains IDEA's text-file encoding.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/14
 */
class KttpDocumentSaverTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()

    fun testRepeatedLargeEditsAreImmediatelyVisibleToExternalReaders() {
        val file = myFixture.addFileToProject("saved.kttp.kts", "// original\n").virtualFile
        val documents = FileDocumentManager.getInstance()
        val document = documents.getDocument(file)!!
        repeat(8) { iteration ->
            val source = "// " + "x".repeat(262_144) + "\nGET(\"https://example.test/$iteration\")\n"
            WriteCommandAction.runWriteCommandAction(project) { document.setText(source) }
            KttpDocumentSaver.save(document)
            assertEquals(source, Files.readString(Path.of(file.path)))
            assertFalse(documents.isDocumentUnsaved(document))
        }
    }

    fun testPreservesUtf16EncodingAndCrLfLineSeparators() {
        val file = myFixture.tempDirFixture.createFile("encoded.kttp.kts")
        WriteCommandAction.runWriteCommandAction(project) {
            file.charset = Charsets.UTF_16LE
            file.setBinaryContent(
                byteArrayOf(
                    0xff.toByte(),
                    0xfe.toByte()
                ) + "original\r\n".toByteArray(Charsets.UTF_16LE)
            )
        }
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText("更新\nsecond\n") }
        KttpDocumentSaver.save(document)
        assertEquals("更新\r\nsecond\r\n", Files.readString(Path.of(file.path), Charsets.UTF_16))
        assertEquals(Charsets.UTF_16LE, file.charset)
        assertTrue(file.contentsToByteArray().contentEquals(Files.readAllBytes(Path.of(file.path))))
    }
}
