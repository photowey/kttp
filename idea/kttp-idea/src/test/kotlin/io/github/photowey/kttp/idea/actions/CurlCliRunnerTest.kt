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
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PathUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
import kotlin.test.assertFailsWith

/**
 * Verifies cURL process output collection, timeout, cancellation, and error handling.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class CurlCliRunnerTest : BasePlatformTestCase() {
    fun testDrainsStderrWhileCollectingCurlStdout() {
        assertEquals("curl 'http://example.test/'", runFixture("stderr"))
    }

    fun testTimeoutTerminatesContinuousProcessWithoutReturningPartialCurl() {
        val error = assertFailsWith<IllegalStateException> { runFixture("wait", timeoutMillis = 1_000) }
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("timed out", ignoreCase = true))
    }

    fun testCancellationTerminatesContinuousProcessWithoutReturningPartialCurl() {
        val indicator = EmptyProgressIndicator()
        assertFailsWith<ProcessCanceledException> {
            runFixture("wait", indicator = indicator) { indicator.cancel() }
        }
    }

    fun testNonzeroExitRejectsStdoutAndReportsStderr() {
        val error = assertFailsWith<IllegalStateException> { runFixture("fail") }
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("fixture evaluation failed"))
    }

    private fun runFixture(
        mode: String,
        timeoutMillis: Int = 2_000,
        indicator: EmptyProgressIndicator = EmptyProgressIndicator(),
        onStarted: () -> Unit = {}
    ): String {
        val directory = Files.createTempDirectory("kttp-curl-process")
        val pidFile = directory.resolve("pid")
        val classpath = listOf(CurlProcessFixture::class.java, Unit::class.java)
            .map { PathUtil.getJarPathForClass(it) }
            .distinct().joinToString(File.pathSeparator)
        val commandLine = GeneralCommandLine(
            File(System.getProperty("java.home"), "bin/java").absolutePath,
            "-cp", classpath, CurlProcessFixture::class.java.name, mode, pidFile.toString()
        )
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "kttp-curl-runner-test").apply { isDaemon = true }
        }
        val future = executor.submit<String> { CurlCliRunner(timeoutMillis).run(commandLine, indicator) }
        try {
            val pid = awaitPid(pidFile)
            onStarted()
            val result = runCatching { future.get(5, TimeUnit.SECONDS) }
            if (result.exceptionOrNull() is java.util.concurrent.TimeoutException) result.getOrThrow()
            val process = ProcessHandle.of(pid).orElse(null)
            process?.onExit()?.get(2, TimeUnit.SECONDS)
            assertFalse("CLI process must terminate before its runner completes", process?.isAlive == true)
            return result.getOrElse { error ->
                throw if (error is ExecutionException) error.cause ?: error else error
            }
        } finally {
            readProcess(pidFile)?.let { process ->
                if (process.isAlive) process.destroyForcibly()
                process.onExit().get(2, TimeUnit.SECONDS)
            }
            future.cancel(true)
            executor.shutdownNow()
            directory.toFile().deleteRecursively()
        }
    }

    private fun awaitPid(pidFile: Path): Long {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (System.nanoTime() < deadline) {
            runCatching { Files.readString(pidFile).trim().toLong() }.getOrNull()?.let { return it }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10))
        }
        error("Java fixture did not publish its PID")
    }

    private fun readProcess(pidFile: Path): ProcessHandle? = runCatching {
        ProcessHandle.of(Files.readString(pidFile).trim().toLong()).orElse(null)
    }.getOrNull()
}
