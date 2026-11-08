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

package io.github.photowey.kttp.cli.execution

import io.github.photowey.kttp.core.context.execution.ExecutionScenario

import java.nio.file.Path

/**
 * Options for the Kttp script evaluator.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/17
 */
data class EvaluatorOptions(
    val executionScenario: ExecutionScenario = ExecutionScenario.File,
    val debugLog: Boolean = false,
    val compilationCache: Boolean = true,
    val executionMode: ExecutionMode = ExecutionMode.DEFAULT,
    val jvmTarget: String = "1.8",
    val responseDir: Path? = null,
    /** Optional localFile base; when absent, the scripting host derives a base from the source location. */
    val baseDir: Path? = null
)
