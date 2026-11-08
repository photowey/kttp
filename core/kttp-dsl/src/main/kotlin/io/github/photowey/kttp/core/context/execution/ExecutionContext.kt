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

import io.github.photowey.kttp.core.context.store.NoEnvironmentSelectedException

/**
 * Registers script operations and selects their execution strategy and scenario.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class ExecutionContext {
    private val _registrations: MutableList<ExecutionRegistration> = mutableListOf()
    private val executionRegistrationCustomizers: MutableList<ExecutionRegistrationCustomizer> = mutableListOf()
    private var registrationOrderCounter = generateSequence(0) { it + 1 }.iterator()

    val registrations get() = _registrations.toList()

    /** Assigns a zero-based registration coordinate, adds an optional name, and applies customizers in order. */
    fun registerExecutable(
        executable: Executable<*>,
        name: String?,
        envKeysUsedUnset: Set<String> = emptySet()
    ) {
        val coordinates = buildSet {
            add(DeclarationCoordinates(registrationOrderCounter.next()))
            if (name != null) {
                add(DeclarationCoordinates(name))
            }
        }
        val executionRegistration = ExecutionRegistration(
            coordinates.toMutableSet(),
            executable,
            DefaultExecutionStrategy(),
            envKeysUsedUnset = envKeysUsedUnset
        )
        val finalRegistration =
            executionRegistrationCustomizers.fold(executionRegistration) { registration, customizer ->
                customizer.customize(registration)
            }
        _registrations.add(finalRegistration)
    }

    fun getExecutionStrategy(executable: Executable<*>): KttpExecutionStrategy {
        return executable.findRegistration().executionStrategy
    }

    /**
     * Executes registrations in order or selects one by coordinates, skipping ignored registrations.
     * Selection does not prevent script evaluation or dependencies from executing earlier.
     * Missing environment requirements and execution failures propagate to the caller.
     */
    fun execute(scenario: ExecutionScenario) {
        when (scenario) {
            is ExecutionScenario.File -> {
                _registrations.forEach { it.execute() }
            }

            is ExecutionScenario.SingleExecution -> {
                val registration = findRegistration(scenario.declarationCoordinates)
                registration.execute()
            }
        }
    }

    /**
     * Runs the registration at index 0 and returns its result (e.g. [okhttp3.Response]).
     * Returns null if there are no registrations or the first registration is ignored;
     * it does not search subsequent registrations. Used by the single-request script evaluator.
     */
    fun runFirstRegistration(): Any? {
        val reg = _registrations.firstOrNull() ?: return null
        if (reg.isIgnored) return null
        return reg.executable.execute()
    }

    /** Skips this executable during the later scenario pass after an earlier dependent execution. */
    fun ignoreOnExecutionPhase(executable: Executable<*>) {
        executable.findRegistration().isIgnored = true
    }

    /** Adds a customizer for future registrations; existing registrations are unchanged. */
    fun addRegistrationCustomizer(executionRegistrationCustomizer: ExecutionRegistrationCustomizer) {
        executionRegistrationCustomizers.add(executionRegistrationCustomizer)
    }

    /**
     * Finds exactly one registration by number, name, or a call site within an inclusive source-line range.
     * Throws IllegalArgumentException when the selection is missing or ambiguous.
     */
    fun findRegistration(coordinates: DeclarationCoordinates): ExecutionRegistration {
        val matches = _registrations.filter { registration ->
            if (coordinates is DeclarationCoordinates.SourceLines) registration.coordinates.any {
                it is DeclarationCoordinates.SourceLines && it.first in coordinates.first..coordinates.last
            } else coordinates in registration.coordinates
        }
        require(matches.size <= 1) {
            "Ambiguous request selection '$coordinates': ${matches.size} registrations. Use a unique request name or separate call sites."
        }
        val registration = matches.singleOrNull()
        requireNotNull(registration) {
            "No registration found for coordinates '$coordinates'. " +
                "Available registrations: ${_registrations.map { it.coordinates }}"
        }
        return registration
    }

    private fun Executable<*>.findRegistration(): ExecutionRegistration {
        val registration = _registrations.find { it.executable == this }
        return requireNotNull(registration) {
            "Executable '$this' is not registered"
        }
    }

    fun addCoordinatesForExecutable(
        coordinates: DeclarationCoordinates,
        executable: Executable<*>
    ) {
        executable.findRegistration().coordinates.add(coordinates)
    }

    private fun ExecutionRegistration.execute() {
        if (isIgnored) {
            return
        }
        if (envKeysUsedUnset.isNotEmpty()) {
            val keys = envKeysUsedUnset.joinToString(", ")
            throw NoEnvironmentSelectedException(
                "No environment selected. This request uses environment variable(s): $keys. " +
                    "Please select an environment (e.g. dev/test) in Run Configuration or use --env-file / --env."
            )
        }
        executable.execute()
    }
}

/**
 * Adjusts a registration as it is added to the execution context.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
fun interface ExecutionRegistrationCustomizer {
    fun customize(registration: ExecutionRegistration): ExecutionRegistration
}

/**
 * Associates an executable with its coordinates, execution strategy, and environment requirements.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
data class ExecutionRegistration(
    val coordinates: MutableSet<DeclarationCoordinates>,
    val executable: Executable<*>,
    var executionStrategy: KttpExecutionStrategy,
    var isIgnored: Boolean = false,
    val envKeysUsedUnset: Set<String> = emptySet()
)

fun ExecutionRegistration.hasCoordinates(vararg coordinates: DeclarationCoordinates): Boolean =
    coordinates.any { it in this.coordinates }
