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

package io.github.photowey.kttp.script

/**
 * 从脚本文本中提取第一个匹配正则模式的 HTTP 方法和 URL 文本，不解析 Kotlin 语法或编译脚本。
 * 匹配 GET("url")、POST("url") 等仅含一个字面量参数的文本；不会过滤注释或字符串中的伪调用，
 * 不支持命名参数或动态 URL 求值，也不保证匹配结果对应实际执行的请求。
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
object KttpScriptParser {

    private val METHOD_URL_REGEX = Regex(
        """(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\s*\(\s*["']([^"']+)["']\s*\)"""
    )

    /**
     * Contains the method and URL text extracted by the lightweight script parser.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    data class ParsedRequest(val method: String, val url: String)

    /**
     * 返回首个文本匹配的 method 和 url；模板表达式保持原样，无匹配时返回 null。
     */
    fun parseFirstRequest(scriptContent: String): ParsedRequest? {
        return METHOD_URL_REGEX.find(scriptContent)?.let { match ->
            ParsedRequest(
                method = match.groupValues[1].uppercase(),
                url = match.groupValues[2].trim()
            )
        }
    }
}
