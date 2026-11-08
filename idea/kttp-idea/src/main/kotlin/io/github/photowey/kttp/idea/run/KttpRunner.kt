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

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.runners.RunContentBuilder
import com.intellij.openapi.diagnostic.Logger
import org.jdom.Element

private val LOG = Logger.getInstance(KttpRunner::class.java)

/**
 * Empty runner settings; [ProgramRunner] requires a concrete [RunnerSettings] type.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
private class KttpRunnerSettings : RunnerSettings {
    override fun readExternal(element: Element) {}
    override fun writeExternal(element: Element) {}
}

/**
 * ProgramRunner for Debug execution of [KttpRunConfiguration].
 * Run execution is handled by the platform's default runner.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpRunner : ProgramRunner<KttpRunnerSettings> {

    override fun getRunnerId(): String = RUNNER_ID

    override fun canRun(executorId: String, profile: com.intellij.execution.configurations.RunProfile): Boolean {
        // Handle Debug here; leave Run to the platform default runner.
        // This way Run tool window is automatically activated by the platform Runner
        val canRun = profile is KttpRunConfiguration && executorId == DefaultDebugExecutor.EXECUTOR_ID
        LOG.info("[KttpRunner] canRun: executorId=$executorId, profile=${profile.javaClass.simpleName}, result=$canRun")
        return canRun
    }

    @Throws(ExecutionException::class)
    override fun execute(environment: ExecutionEnvironment) {
        LOG.info("[KttpRunner] execute called")
        try {
            val state = environment.state
            if (state == null) {
                LOG.error("[KttpRunner] state is null")
                return
            }
            LOG.info("[KttpRunner] executing state: ${state.javaClass.simpleName}")

            val result = try {
                state.execute(environment.executor, this)
            } catch (ex: Exception) {
                LOG.error("[KttpRunner] state.execute threw exception", ex)
                throw ex
            }

            if (result == null) {
                LOG.error("[KttpRunner] state.execute returned null")
                return
            }

            LOG.info("[KttpRunner] ExecutionResult type: ${result.javaClass.simpleName}, showing run content")
            try {
                val contentToReuse = environment.contentToReuse
                LOG.info("[KttpRunner] contentToReuse: $contentToReuse")
                RunContentBuilder(result, environment).showRunContent(contentToReuse)
                LOG.info("[KttpRunner] showRunContent called successfully")
                // Note: Run executor is handled by platform default Runner (JavaApplicationRunner),
                // which automatically activates the Run tool window. Only Debug executor uses KttpRunner.
            } catch (ex: Exception) {
                LOG.error("[KttpRunner] showRunContent threw exception", ex)
                throw ex
            }
        } catch (ex: Exception) {
            LOG.error("[KttpRunner] execute failed", ex)
            throw ExecutionException("Failed to execute Kttp configuration", ex)
        }
    }

    /**
     * Defines the program runner identifier used by IDEA.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    companion object {
        const val RUNNER_ID = "KttpRunner"
    }
}
