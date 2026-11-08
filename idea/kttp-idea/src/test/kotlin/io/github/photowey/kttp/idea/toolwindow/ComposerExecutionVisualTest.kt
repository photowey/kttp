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

package io.github.photowey.kttp.idea.toolwindow

import com.intellij.testFramework.LightPlatformTestCase
import java.awt.Container
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * Renders the execution controls at a narrow width for layout verification.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class ComposerExecutionVisualTest : LightPlatformTestCase() {
    fun testRenderExecutionControlsAtNarrowWidth() {
        val header = ComposerHeaderPanel()
        header.setSize(650, header.preferredSize.height)
        fun layout(component: Container) {
            component.doLayout()
            component.components.filterIsInstance<Container>().forEach(::layout)
        }
        layout(header)
        header.setSize(650, header.preferredSize.height)
        layout(header)
        val image = BufferedImage(header.width, header.height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().let { graphics -> header.printAll(graphics); graphics.dispose() }
        val directory = Path.of(System.getProperty("user.dir"), "build/reports/dual-execution/visual")
        Files.createDirectories(directory)
        ImageIO.write(image, "png", directory.resolve("composer-current.png").toFile())
        assertTrue(header.executionModeCombo.isVisible)
        val response = ResponseTabPanel(project)
        response.showResponse(
            200, mapOf("Content-Type" to "application/json"), "{}", responseFilePath = null,
            executionMode = io.github.photowey.kttp.idea.execution.RequestExecutionMode.FOLLOW_SCRIPT,
            totalTimeMs = 560, httpTimeMs = 7, executionFailure = "then assertion failed"
        )
        response.setSize(650, 300)
        layout(response)
        val resultImage = BufferedImage(650, 300, BufferedImage.TYPE_INT_RGB)
        resultImage.createGraphics().let { graphics -> response.printAll(graphics); graphics.dispose() }
        ImageIO.write(resultImage, "png", directory.resolve("response-current.png").toFile())
    }
}
