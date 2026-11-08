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

package io.github.photowey.kttp.core.extension.okhttp

import okhttp3.MediaType

fun MediaType.hasSubTypeOf(subtype: String) =
    this.subtype.equals(subtype, ignoreCase = true)

fun MediaType.hasTypeOf(type: String) =
    this.type.equals(type, ignoreCase = true)

fun MediaType.fileExtension() = when (this.toString().lowercase()) {
    "text/plain" -> "txt"
    "text/html" -> "html"
    "text/css" -> "css"
    "application/json" -> "json"
    "application/xml" -> "xml"
    "application/pdf" -> "pdf"
    "application/zip" -> "zip"
    "application/gzip" -> "gz"
    "image/png" -> "png"
    "image/jpeg" -> "jpg"
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    "audio/mpeg" -> "mp3"
    "video/mp4" -> "mp4"
    "application/msword" -> "doc"
    "application/vnd.ms-excel" -> "xls"
    "application/vnd.ms-powerpoint" -> "ppt"
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
    "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
    else -> this.subtype.takeIf { it.isValidFileExtension() }
}

private fun String.isValidFileExtension() =
    this.matches(Regex("^[a-zA-Z0-9]+$"))