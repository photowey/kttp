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

package io.github.photowey.kttp.idea.actions

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException

/**
 * Runs the cURL-generation CLI process with output collection, timeout, and cancellation handling.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal class CurlCliRunner(private val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS) {
    fun run(commandLine: GeneralCommandLine, indicator: ProgressIndicator): String {
        indicator.checkCanceled()
        val output = CapturingProcessHandler(commandLine)
            .runProcessWithProgressIndicator(indicator, timeoutMillis, true)
        if (output.isCancelled || indicator.isCanceled) throw ProcessCanceledException()
        check(!output.isTimeout) { "Kttp CLI timed out after ${timeoutMillis / 1000.0} seconds" }
        check(output.exitCode == 0) {
            output.stderr.trim().ifBlank { "Kttp CLI exited with code ${output.exitCode}" }
        }
        return output.stdout.trim()
    }

    /**
     * Defines the default time limit for cURL-generation processes.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 60_000
    }
}
