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

package io.github.photowey.kttp.core.printer

import io.github.photowey.kttp.core.printer.Printer.Color
import io.github.photowey.kttp.core.printer.Printer.Companion.RESET_COLOR

/**
 * Writes script output with optional semantic color information.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
interface Printer {

    fun print(text: String, color: Color? = null)

    /**
     * Maps the supported output colors to ANSI escape sequences.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    enum class Color(val code: String) {
        RED("31"),
        GREEN("32"),
        BLUE("34");

        fun ansi(): String {
            return "\u001B[${code}m"
        }
    }

    /**
     * Defines the ANSI sequence that resets output colors.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    companion object {
        const val RESET_COLOR = "\u001B[0m"
    }
}

fun Printer.println(text: String = "", color: Color? = null) {
    print(text + "\n", color)
}

fun Printer.debugln(text: String) {
    println(text)
}

/**
 * Adds ANSI color sequences around colored output.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
abstract class AbstractColorPrinter : Printer {

    final override fun print(text: String, color: Color?) {
        if (color != null) {
            print(color.ansi())
        }

        print(text)

        if (color != null) {
            print(RESET_COLOR)
        }
    }

    protected abstract fun print(s: String)
}

/**
 * Discards color metadata while forwarding the output text.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
abstract class AbstractNonColorPrinter : Printer {

    final override fun print(text: String, color: Color?) {
        print(text)
    }

    protected abstract fun print(s: String)
}

/**
 * Writes colored script output to the process standard output stream.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
object SystemOutPrinter : AbstractColorPrinter() {
    override fun print(s: String) {
        kotlin.io.print(s)
    }
}

/**
 * No-op printer for headless/IDE execution (no console output).
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
object NoopPrinter : AbstractNonColorPrinter() {
    override fun print(s: String) {}
}
