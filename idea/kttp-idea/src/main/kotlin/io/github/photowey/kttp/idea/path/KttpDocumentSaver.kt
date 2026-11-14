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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.SavingRequestor
import com.intellij.openapi.vfs.SafeWriteRequestor

/**
 * Finishes local document saves before Kttp hands files to its external CLI or reports an import committed.
 * Preserves IDEA's save transformations, encoding and line separators. The second VFS write uses a synchronous
 * saving requestor and the saved bytes because IDEA 263 may defer FileDocumentManager's physical writes.
 * The save markers preserve document Undo history and IDEA's safe-write policy. Clean read-only files need no rewrite.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/14
 */
internal object KttpDocumentSaver : SavingRequestor, SafeWriteRequestor {
    fun save(document: Document) {
        val documents = FileDocumentManager.getInstance()
        documents.saveDocument(document)
        check(!documents.isDocumentUnsaved(document)) { "Save the file before continuing this Kttp operation." }
        val file = requireNotNull(documents.getFile(document)) { "Cannot locate the saved file." }
        if (file.isInLocalFileSystem && file.isWritable) {
            ApplicationManager.getApplication().runWriteAction {
                file.setBinaryContent(file.contentsToByteArray(), file.modificationStamp, file.timeStamp, this)
            }
        }
    }
}
