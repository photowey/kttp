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
 * Identifies a registered script declaration by number, source lines, or name.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
sealed interface DeclarationCoordinates {

    fun asString(): String

    /**
     * Identifies a registration by its numeric coordinate.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    data class Number(val number: Int) : DeclarationCoordinates {
        override fun asString(): String = number.toString()
    }

    /**
     * Inclusive source lines; runtime registrations record the actual script call site.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    data class SourceLines(val first: Int, val last: Int = first) : DeclarationCoordinates {
        init {
            require(first > 0 && last >= first) { "Invalid request source lines" }
        }

        override fun asString(): String = "$first:$last"
    }

    /**
     * Identifies a registration by its explicit or delegated property name.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    data class Name(val name: String) : DeclarationCoordinates {
        override fun asString(): String = name
    }
}

fun DeclarationCoordinates(number: Int) = DeclarationCoordinates.Number(number)

fun DeclarationCoordinates(name: String) = DeclarationCoordinates.Name(name)
