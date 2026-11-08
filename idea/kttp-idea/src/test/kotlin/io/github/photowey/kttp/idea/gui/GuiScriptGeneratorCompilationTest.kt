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

package io.github.photowey.kttp.idea.gui

import io.github.photowey.kttp.core.enums.HttpMethod
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compile against the real DSL so unresolved env names and delegate/String mismatches cannot pass.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class GuiScriptGeneratorCompilationTest {

    @Test
    fun environmentReferencesCompileWithoutExternalDeclarations() {
        val draft = draft(
            target = RequestTarget(
                ValueRef.Env("base-url"),
                queryParams = listOf(NameValueRef("mode", ValueRef.Env("when")))
            ),
            headers = listOf(HeaderSpec("X-Mode", ValueRef.Env("when"))),
            auth = AuthSpec.Basic(ValueRef.Env("username"), ValueRef.Env("password"))
        )

        assertCompiles(GuiScriptGenerator.render(draft))
    }

    @Test
    fun localFileBodyCompilesAsTextWithExplicitEncoding() {
        val draft = draft(
            target = RequestTarget(ValueRef.Literal("https://example.com")),
            body = BodySpec.Raw(ValueRef.LocalFile("payload.json", charset = "UTF-16LE", required = false))
        )

        assertCompiles(GuiScriptGenerator.render(draft))
    }

    @Test
    fun appendedRequestsReuseEnvDeclarationsAndAvoidExistingFileVariableNames() {
        val existing = """
            val `base-url`: String by env
            val kttpFile0 = "existing user value"
            GET("https://example.com/first") {}
        """.trimIndent() + "\n"
        val draft = draft(
            target = RequestTarget(ValueRef.Env("base-url")),
            body = BodySpec.Json(ValueRef.LocalFile("payload.json"))
        )

        assertCompiles(existing + GuiScriptGenerator.render(draft, existing))
    }

    private fun draft(
        target: RequestTarget,
        headers: List<HeaderSpec> = emptyList(),
        auth: AuthSpec = AuthSpec.Inherit,
        body: BodySpec = BodySpec.None
    ) = GuiDraft(
        id = "compile-contract",
        title = "Compile contract",
        ownership = DraftOwnership.GUI_OWNED,
        request = RequestSpec(method = HttpMethod.POST, target = target, headers = headers, auth = auth, body = body)
    )

    private fun assertCompiles(script: String) {
        val directory = Files.createTempDirectory("kttp-generated-compile").toFile()
        try {
            val source = directory.resolve("generated.kttp.kts").apply { writeText(script) }
            val output = directory.resolve("compiler.log")
            val cliJar = File(requireNotNull(System.getProperty("kttp.test.cliJar")) {
                "The test task must provide the built CLI fat JAR using kttp.test.cliJar"
            })
            assertTrue(cliJar.isFile, "CLI fat JAR must exist: $cliJar")
            val process = ProcessBuilder(
                File(System.getProperty("java.home"), "bin/java").absolutePath,
                "-jar", cliJar.absolutePath,
                "--script", source.absolutePath,
                "--execution-mode", "COMPILE_ONLY",
                "--no-compilation-cache", "--json-output"
            ).redirectErrorStream(true).redirectOutput(output).start()
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                error("Generated script compilation timed out:\n${output.readText()}")
            }
            assertEquals(
                0,
                process.exitValue(),
                "Generated script must compile in the real CLI:\n$script\n${output.readText()}"
            )
        } finally {
            directory.deleteRecursively()
        }
    }
}
