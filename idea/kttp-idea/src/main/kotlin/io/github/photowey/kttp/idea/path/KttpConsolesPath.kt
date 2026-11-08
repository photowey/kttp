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

import com.intellij.openapi.application.PathManager
import java.io.File

/**
 * Kttp 控制台脚本、响应和后备草稿状态的目录约定。
 *
 * 使用当前 IDE 的 [PathManager.getConfigPath] 拼接 consoles/Kttp，与控制台根节点配合使用。
 * 配置目录由 IDE 产品、版本、运行方式及用户设置决定，不固定为开发机或 runIde 沙箱路径。
 * 项目内生成脚本和快速请求响应的目录见 [KttpProjectPath]。
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
object KttpConsolesPath {

    private const val CONSOLES_KTPP = "consoles/Kttp"
    private const val RESPONSES = "responses"
    private const val GUI_STATE = "gui-state"

    /** 当前 IDE 配置目录；调用者再拼接 consoles/Kttp。 */
    private fun getConsolesBaseDir(): File = File(PathManager.getConfigPath())

    /**
     * Kttp 控制台根目录，脚本与响应均在其下。
     */
    fun getKttpConsolesDir(): File {
        val dir = File(getConsolesBaseDir(), CONSOLES_KTPP)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 响应文件存储目录。
     */
    fun getResponsesDir(): File {
        val dir = File(getKttpConsolesDir(), RESPONSES)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * GUI 状态目录。
     */
    fun getGuiStateDir(): File {
        val dir = File(getKttpConsolesDir(), GUI_STATE)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
