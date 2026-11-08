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

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.LazyFileHyperlinkInfo
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nullable

/**
 * Console filter that makes response file paths clickable.
 * Detects "Response file saved." followed by "> /path/to/file.json" and creates clickable links.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/18
 */
class KttpResponseFileLinkFilter(private val project: Project) : Filter {

    @Nullable
    private var prevLine: String? = null

    @Nullable
    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val result = processFileLine(line, entireLength)
        prevLine = line
        return result
    }

    private fun processFileLine(line: String, entireLength: Int): Filter.Result? {
        // Check if previous line was "Response file saved."
        if (prevLine != "Response file saved.\n") {
            return null
        }

        // Check if current line starts with "> "
        if (!line.startsWith("> ")) {
            return null
        }

        val filepathStartOffset = entireLength - line.length
        val prefixLength = 2
        var filePath = line.drop(prefixLength)
        val actualFilepathStartOffset = filepathStartOffset + prefixLength

        // Remove "file://" prefix if present
        if (filePath.startsWith("file://")) {
            filePath = filePath.drop(7)
        }

        // Trim whitespace (including newline if present)
        val hasNewline = line.endsWith("\n")
        filePath = filePath.trimEnd()

        // Create file hyperlink info
        val responseFileHyperlinkInfo = LazyFileHyperlinkInfo(
            project,
            filePath,
            0,
            0,
            false
        )

        // Place the reveal-file inlay after the path line, including its newline when present.
        val inlayOffset = entireLength
        val hyperlinkEndOffset = if (hasNewline) entireLength - 1 else entireLength

        // Create two result items:
        // 1. RevealFileInlay - shows the platform's reveal-file action after the path line
        // 2. File path hyperlink - makes the file path clickable
        val resultItems = listOf(
            RevealFileInlay(inlayOffset, filePath),
            Filter.ResultItem(
                actualFilepathStartOffset,
                hyperlinkEndOffset,
                responseFileHyperlinkInfo as HyperlinkInfo
            )
        )

        return Filter.Result(resultItems)
    }
}
