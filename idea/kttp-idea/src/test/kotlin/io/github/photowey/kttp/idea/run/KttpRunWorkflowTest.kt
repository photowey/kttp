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

import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import io.github.photowey.kttp.core.context.execution.DeclarationCoordinates
import io.github.photowey.kttp.idea.io.HistoryPersistence
import java.io.File

/**
 * Verifies run configuration matching, script selection, and editor-save behavior.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class KttpRunWorkflowTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()

    fun testSameFileRequiresMatchingRequestNumber() {
        val (first, second) = requestContexts()
        val producer = KttpRunConfigurationProducer()
        val configuration = configuration(first.location!!.virtualFile!!.path)
        configuration.runStrategy = ScriptRunStrategy.SingleDeclaration(DeclarationCoordinates.Number(1))
        assertTrue(producer.isConfigurationFromContext(configuration, first))
        assertFalse(producer.isConfigurationFromContext(configuration, second))
        configuration.runStrategy = ScriptRunStrategy.SingleDeclaration(DeclarationCoordinates.Number(2))
        assertFalse(producer.isConfigurationFromContext(configuration, first))
        assertTrue(producer.isConfigurationFromContext(configuration, second))
    }

    fun testWholeFileConfigurationDoesNotMatchAnIndividualRequest() {
        val (first, _) = requestContexts()
        val configuration = configuration(first.location!!.virtualFile!!.path)
        configuration.runStrategy = ScriptRunStrategy.WholeFile
        assertFalse(KttpRunConfigurationProducer().isConfigurationFromContext(configuration, first))
    }

    fun testFileContextCreatesAndMatchesWholeFileConfiguration() {
        val (first, second) = requestContexts()
        val psiFile = first.psiLocation!!.containingFile
        val fileContext = ConfigurationContext.createEmptyContextForLocation(
            PsiLocation.fromPsiElement(project, psiFile)
        )
        val producer = KttpRunConfigurationProducer()
        val generated = producer.createConfigurationFromContext(fileContext)
            ?: error("Expected a run configuration for the selected script file")
        val configuration = generated.configuration as KttpRunConfiguration
        assertEquals(psiFile.virtualFile.path, configuration.scriptPath)
        assertEquals(ScriptRunStrategy.WholeFile, configuration.runStrategy)
        assertTrue(producer.isConfigurationFromContext(configuration, fileContext))
        assertFalse(producer.isConfigurationFromContext(configuration, first))
        assertFalse(producer.isConfigurationFromContext(configuration, second))

        configuration.runStrategy = ScriptRunStrategy.SingleDeclaration(DeclarationCoordinates.Number(1))
        assertFalse(producer.isConfigurationFromContext(configuration, fileContext))
        assertTrue(producer.isConfigurationFromContext(configuration, first))
    }

    fun testSameFileRequiresMatchingNamedRequest() {
        val text =
            "GET(\"https://example.com/first\", name = \"first\")\nPOST(\"https://example.com/second\", name = \"second\")\n"
        val (first, second) = requestContexts(text)
        val configuration = configuration(first.location!!.virtualFile!!.path)
        configuration.runStrategy = ScriptRunStrategy.SingleDeclaration(DeclarationCoordinates.Name("first"))
        val producer = KttpRunConfigurationProducer()
        assertTrue(producer.isConfigurationFromContext(configuration, first))
        assertFalse(producer.isConfigurationFromContext(configuration, second))
    }

    fun testRunResolvesSavedRequestCoordinatesToVerifiedSourceLines() {
        val (first, _) = requestContexts()
        val configuration = configuration(first.location!!.virtualFile!!.path)
        ApplicationManager.getApplication().runWriteAction {
            val sdk = IdeaTestUtil.getMockJdk21()
            ProjectJdkTable.getInstance().addJdk(sdk, testRootDisposable)
            ProjectRootManager.getInstance(project).projectSdk = sdk
        }
        val previousCliJar = System.getProperty("kttp.cli.jar")
        try {
            System.setProperty("kttp.cli.jar", System.getProperty("kttp.test.cliJar"))
            for (number in 1..2) {
                configuration.runStrategy = ScriptRunStrategy.SingleDeclaration(DeclarationCoordinates.Number(number))
                val environment = ExecutionEnvironmentBuilder.create(
                    project, DefaultRunExecutor.getRunExecutorInstance(), configuration
                ).build()
                val arguments = KttpRunProfileState(configuration, environment).javaParameters
                    .programParametersList.parameters
                val sourceIndex = arguments.indexOf("--request-lines")
                assertTrue(sourceIndex >= 0)
                assertEquals("$number:$number", arguments[sourceIndex + 1])
                assertFalse(arguments.contains("--request-number"))
                val manifestIndex = arguments.indexOf("--response-manifest")
                assertTrue(manifestIndex >= 0)
                val manifest = File(arguments[manifestIndex + 1])
                assertTrue(manifest.isFile)
                manifest.delete()
            }
        } finally {
            if (previousCliJar == null) System.clearProperty("kttp.cli.jar")
            else System.setProperty("kttp.cli.jar", previousCliJar)
        }
    }

    fun testComputedRequestNameFromGutterUsesSourcePositionRatherThanExpressionText() {
        val (first, _) = requestContexts("val computedName = \"foo\"\nGET(\"https://example.com/first\", name = computedName)\nPOST(\"https://example.com/second\")\n")
        val generated = KttpRunConfigurationProducer().createConfigurationFromContext(first)
            ?: error("Expected a request configuration")
        val configuration = generated.configuration as KttpRunConfiguration
        assertEquals(ScriptRunStrategy.SingleDeclaration(DeclarationCoordinates.Number(1)), configuration.runStrategy)
    }

    fun testNamedRunConfigurationsKeepRuntimeNamesForPositionalAndComputedNames() {
        val source = myFixture.addFileToProject(
            "named-compatibility.kttp.kts", """
            val computedName = "foo"
            GET("https://example.com/one", "foo")
            GET("https://example.com/two", name = computedName)
        """.trimIndent()
        )
        val configuration = configuration(source.virtualFile.path)
        configuration.runStrategy = ScriptRunStrategy.SingleDeclaration(DeclarationCoordinates.Name("foo"))
        ApplicationManager.getApplication().runWriteAction {
            val sdk = IdeaTestUtil.getMockJdk21()
            ProjectJdkTable.getInstance().addJdk(sdk, testRootDisposable)
            ProjectRootManager.getInstance(project).projectSdk = sdk
        }
        val previousCliJar = System.getProperty("kttp.cli.jar")
        try {
            System.setProperty("kttp.cli.jar", System.getProperty("kttp.test.cliJar"))
            val environment =
                ExecutionEnvironmentBuilder.create(project, DefaultRunExecutor.getRunExecutorInstance(), configuration)
                    .build()
            val arguments =
                KttpRunProfileState(configuration, environment).javaParameters.programParametersList.parameters
            val index = arguments.indexOf("--request-name")
            assertTrue(index >= 0)
            assertEquals("foo", arguments[index + 1])
            assertFalse(arguments.contains("--request-lines"))
            File(arguments[arguments.indexOf("--response-manifest") + 1]).delete()
        } finally {
            if (previousCliJar == null) System.clearProperty("kttp.cli.jar") else System.setProperty(
                "kttp.cli.jar",
                previousCliJar
            )
        }
    }

    fun testRunHistoryAppendsResponsesInChronologicalOrder() {
        val old = HistoryPersistence.Record("old", "/old.json", "/old.kttp.kts")
        val newest = File("/newest.json")
        val earlier = File("/earlier.json")
        val records = KttpRunProfileState.appendResponseHistory(
            listOf(old), listOf(earlier, newest), "new", "/new.kttp.kts"
        )
        assertEquals(listOf("/old.json", "/earlier.json", "/newest.json"), records.map { it.responseFilePath })
        assertEquals(listOf("old", "new", "new"), records.map { it.scriptName })
    }

    fun testRunHistoryRetainsNewestResponseWhenAlreadyFull() {
        val previous = HistoryPersistence.load()
        try {
            val existing = (0 until 100).map { HistoryPersistence.Record("old-$it", "/old-$it.json") }
            val merged = KttpRunProfileState.appendResponseHistory(
                existing, listOf(File("/latest.json")), "latest", "/new.kttp.kts"
            )
            HistoryPersistence.clear()
            HistoryPersistence.append(merged)
            val saved = HistoryPersistence.load()
            assertEquals(100, saved.size)
            assertEquals("/old-1.json", saved.first().responseFilePath)
            assertEquals("/latest.json", saved.last().responseFilePath)
        } finally {
            HistoryPersistence.clear()
            HistoryPersistence.append(previous)
        }
    }

    private fun configuration(path: String): KttpRunConfiguration =
        (KttpRunConfigurationType().createTemplateConfiguration(project) as KttpRunConfiguration).apply {
            scriptPath = path
        }

    private fun requestContexts(
        text: String = "GET(\"https://example.com/first\")\nPOST(\"https://example.com/second\")\n"
    ): Pair<ConfigurationContext, ConfigurationContext> {
        val file = myFixture.tempDirFixture.createFile("requests.kttp.kts", text)
        val psi = myFixture.psiManager.findFile(file) ?: error("No PSI for ${file.path}")
        fun context(offset: Int) = ConfigurationContext.createEmptyContextForLocation(
            PsiLocation.fromPsiElement(project, psi.findElementAt(offset) ?: error("No element at $offset"))
        )
        return context(text.indexOf("GET") + 1) to context(text.indexOf("POST") + 1)
    }
}
