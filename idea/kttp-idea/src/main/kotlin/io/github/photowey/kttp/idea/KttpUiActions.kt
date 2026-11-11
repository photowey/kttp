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

package io.github.photowey.kttp.idea

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project

/**
 * Schedules model and editor operations on IDEA's write-safe UI queue.
 * Swing accessibility callbacks may run on EDT without the platform's write-intent lock.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/11
 */
internal object KttpUiActions {
    /** Defers the operation until a non-modal UI context and skips disposed projects. */
    fun submit(project: Project, action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater({
            if (!project.isDisposed) action()
        }, ModalityState.nonModal())
    }
}
