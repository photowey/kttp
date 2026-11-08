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

import io.github.photowey.kttp.core.context.execution.DeclarationCoordinates

/**
 * Extension functions for KttpRunConfigurationOptions.
 */

/**
 * Get run strategy from options.
 */
fun KttpRunConfigurationOptions.getRunStrategy(): ScriptRunStrategy {
    return when (runStrategySelection) {
        ScriptRunStrategySelection.WHOLE_FILE -> ScriptRunStrategy.WholeFile
        ScriptRunStrategySelection.SINGLE_DECLARATION -> {
            val coords = coordinates
            if (coords != null) {
                val number = coords.toIntOrNull()
                if (number != null) {
                    ScriptRunStrategy.SingleDeclaration(DeclarationCoordinates.Number(number))
                } else {
                    ScriptRunStrategy.SingleDeclaration(DeclarationCoordinates.Name(coords))
                }
            } else {
                ScriptRunStrategy.WholeFile
            }
        }
    }
}

/**
 * Set run strategy to options.
 */
fun KttpRunConfigurationOptions.setRunStrategy(value: ScriptRunStrategy) {
    when (value) {
        is ScriptRunStrategy.WholeFile -> {
            runStrategySelection = ScriptRunStrategySelection.WHOLE_FILE
            coordinates = null
        }

        is ScriptRunStrategy.SingleDeclaration -> {
            runStrategySelection = ScriptRunStrategySelection.SINGLE_DECLARATION
            coordinates = value.coordinates.asString()
        }
    }
}
