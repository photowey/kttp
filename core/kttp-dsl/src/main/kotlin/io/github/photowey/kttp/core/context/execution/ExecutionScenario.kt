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

package io.github.photowey.kttp.core.context.execution


/**
 * Selects whether to execute the whole script registration set or one declaration.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
sealed interface ExecutionScenario {

    /**
     * Selects the script's registered operations for file execution.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    object File : ExecutionScenario

    /**
     * Selects one declaration using its registration coordinates.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    data class SingleExecution(val declarationCoordinates: DeclarationCoordinates) : ExecutionScenario

    /**
     * Creates single-declaration scenarios from a request number or name.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    companion object {
        fun SingleExecution(number: Int) = SingleExecution(DeclarationCoordinates(number))
        fun SingleExecution(name: String) = SingleExecution(DeclarationCoordinates(name))
    }
}