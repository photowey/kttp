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

import io.github.photowey.kttp.core.context.execution.DeclarationCoordinates

/**
 * 脚本运行策略
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/17
 */
sealed class ScriptRunStrategy {
    /**
     * 执行整个文件
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/17
     */
    object WholeFile : ScriptRunStrategy()

    /**
     * 执行单个请求声明
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/17
     */
    data class SingleDeclaration(val coordinates: DeclarationCoordinates) : ScriptRunStrategy()
}

/**
 * 用于持久化的策略选择
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/17
 */
enum class ScriptRunStrategySelection {
    WHOLE_FILE,
    SINGLE_DECLARATION
}
