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

package io.github.photowey.kttp.idea.path

import java.io.File

/**
 * 项目级 Kttp 目录约定（相对于项目根）：
 *
 * - **请求脚本**：`./.kttp/requests` — GUI 生成的项目内 .kttp.kts 请求脚本
 * - **临时脚本**：`./kttp/temp` — 未选择已有脚本时通过 Add Request 创建的脚本
 * - **响应文件**：`./.kttp/responses` — GUI 快速请求的项目内响应文件
 * - **GUI 状态**：`./.kttp/gui-state` — 项目内草稿状态
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/21
 */
object KttpProjectPath {

    private const val KTPP = ".kttp"
    private const val REQUESTS = "requests"
    private const val PROJECT_TEMP = "kttp/temp"
    private const val RESPONSES = "responses"
    private const val GUI_STATE = "gui-state"

    /**
     * 项目内请求脚本目录：./.kttp/requests
     */
    fun getRequestsDir(projectBasePath: String): File {
        val dir = File(projectBasePath, "$KTPP/$REQUESTS")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Project-local temporary scripts: ./kttp/temp */
    fun getTempRequestsDir(projectBasePath: String): File {
        val dir = File(projectBasePath, PROJECT_TEMP)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 项目内响应文件目录：./.kttp/responses
     */
    fun getResponsesDir(projectBasePath: String): File {
        val dir = File(projectBasePath, "$KTPP/$RESPONSES")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 项目内 GUI 状态目录：./.kttp/gui-state
     */
    fun getGuiStateDir(projectBasePath: String): File {
        val dir = File(projectBasePath, "$KTPP/$GUI_STATE")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
