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

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import io.github.photowey.kttp.idea.cli.KttpCliInvocation
import io.github.photowey.kttp.idea.cli.KttpCliLocator
import java.io.File

/**
 * Builds the CLI launch parameters shared by script Run and GUI Follow Script.
 */
fun kttpRunScriptJavaParameters(
    project: Project,
    invocation: KttpCliInvocation,
    configure: JavaParameters.() -> Unit = {}
): JavaParameters {
    val params = JavaParameters()

    // Set JDK
    var sdk: Sdk? = ProjectRootManager.getInstance(project).projectSdk
    if (sdk == null) {
        val allJdks = ProjectJdkTable.getInstance().allJdks
        if (allJdks.isNotEmpty()) {
            sdk = allJdks[0]
        }
    }
    if (sdk == null) {
        throw CantRunException("No JDK configured for project")
    }
    params.jdk = sdk

    // Use the project root as working directory, or the script directory when no project root is available.
    // The CLI's baseDir argument separately controls localFile resolution.
    val projectRoot = project.basePath
    if (!projectRoot.isNullOrEmpty()) {
        params.workingDirectory = projectRoot
    } else {
        File(invocation.scriptPath).parentFile?.let { parent ->
            params.workingDirectory = parent.absolutePath
        }
    }

    // VM parameters
    params.vmParametersList.add("-ea")

    // Main class
    params.mainClass = "io.github.photowey.kttp.cli.command.KttpCommandKt"

    // Classpath - use CLI JAR
    val cliJar: File = KttpCliLocator.findCliJar(project.basePath)
        ?: throw CantRunException("kttp-cli.jar not found. Please build with: ./gradlew :core:kttp-cli:shadowJar")
    params.classPath.add(cliJar.absolutePath)

    params.programParametersList.addAll(invocation.toProgramArguments())
    configure(params)

    return params
}
