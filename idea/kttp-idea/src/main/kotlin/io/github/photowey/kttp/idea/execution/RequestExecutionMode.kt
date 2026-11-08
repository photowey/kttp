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

package io.github.photowey.kttp.idea.execution

import kotlinx.serialization.Serializable

/**
 * Execution choice is independent of a request's static projection capabilities.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
@Serializable
enum class RequestExecutionMode(val displayName: String) {
    FOLLOW_SCRIPT("Follow Script"),
    QUICK_REQUEST("Quick Request");

    override fun toString(): String = displayName
}
