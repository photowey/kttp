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

import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Provides subprocess behaviors used to test cURL runner lifecycle handling.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
object CurlProcessFixture {
    @JvmStatic
    fun main(args: Array<String>) {
        Files.writeString(Path.of(args[1]), ProcessHandle.current().pid().toString())
        when (args[0]) {
            "stderr" -> {
                System.err.write(ByteArray(1024 * 1024) { 'e'.code.toByte() })
                System.err.flush()
                println("curl 'http://example.test/'")
            }

            "wait" -> {
                println("curl 'http://unfinished.test/'")
                System.out.flush()
                while (true) Thread.sleep(1000)
            }

            "fail" -> {
                println("curl 'http://must-not-copy.test/'")
                System.err.println("fixture evaluation failed")
                exitProcess(9)
            }

            else -> error(args[0])
        }
    }
}
