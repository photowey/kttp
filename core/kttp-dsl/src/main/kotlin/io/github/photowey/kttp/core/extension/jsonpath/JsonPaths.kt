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

package io.github.photowey.kttp.core.extension.jsonpath

import com.jayway.jsonpath.ReadContext
import com.jayway.jsonpath.TypeRef
import io.github.photowey.kttp.core.constant.LANGUAGE_JSON_PATH
import okhttp3.Response
import org.intellij.lang.annotations.Language

/**
 * Exposes JSONPath extraction helpers to scripts.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
interface JsonPathExtension {
    fun Response.jsonPath(): ReadContext
    fun <T> ReadContext.readList(
        @Language(LANGUAGE_JSON_PATH) path: String,
        clazz: Class<T>
    ): List<T>
}

// ----------------------------------------------------------------

inline fun <reified T> ReadContext.doRead(@Language(LANGUAGE_JSON_PATH) path: String): T {
    return read(path, object : TypeRef<T>() {})
}

// ----------------------------------------------------------------

fun ReadContext.readString(@Language(LANGUAGE_JSON_PATH) path: String): String {
    return read(path)
}

fun ReadContext.readInt(@Language(LANGUAGE_JSON_PATH) path: String): Int {
    return read(path)
}

fun ReadContext.readLong(@Language(LANGUAGE_JSON_PATH) path: String): Long {
    return read(path)
}

fun ReadContext.readBoolean(@Language(LANGUAGE_JSON_PATH) path: String): Boolean {
    return read(path)
}

// ----------------------------------------------------------------

fun ReadContext.toStr(@Language(LANGUAGE_JSON_PATH) path: String): String {
    return readString(path)
}

fun ReadContext.toInt(@Language(LANGUAGE_JSON_PATH) path: String): Int {
    return readInt(path)
}

fun ReadContext.toLong(@Language(LANGUAGE_JSON_PATH) path: String): Long {
    return readLong(path)
}

fun ReadContext.toBoolean(@Language(LANGUAGE_JSON_PATH) path: String): Boolean {
    return readBoolean(path)
}
