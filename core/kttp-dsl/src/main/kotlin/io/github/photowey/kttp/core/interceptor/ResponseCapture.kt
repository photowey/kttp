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

package io.github.photowey.kttp.core.interceptor

import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.GzipSource
import okio.buffer
import okio.source
import java.io.IOException
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.UUID

/** Maximum decoded response bytes retained for callbacks and console previews. */
const val MAX_RESPONSE_PREVIEW_BYTES: Long = 64L * 1024
private const val RESPONSE_COPY_BUFFER_BYTES: Long = 8L * 1024

/**
 * Carries a bounded response preview and the body retained for further script processing.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal data class ResponseCapture(
    val response: Response,
    val preview: Response,
    val savedPath: Path? = null
)

/** Peeks a bounded decoded body without consuming the response needed for persistence and script processing. */
internal fun captureResponsePreview(response: Response): Response = previewResponse(
    response, response.body?.let { readPreview(response, it.source().peek()) } ?: ByteArray(0)
)

/** Streams persisted responses and leaves script consumers a complete file-backed body. */
internal fun captureResponse(
    response: Response,
    directory: Path?,
    filename: String,
    isCanceled: () -> Boolean
): ResponseCapture {
    val body = response.body
    if (body == null || body.contentLength() == 0L) {
        val savedPath = directory?.let { target ->
            ResponseStorage.create(target, filename).use { storage ->
                storage.complete()
                storage.path
            }
        }
        return ResponseCapture(response, previewResponse(response, ByteArray(0)), savedPath)
    }
    if (directory == null) {
        val bytes = readPreview(response, body.source().peek())
        return ResponseCapture(response, previewResponse(response, bytes))
    }
    return if (response.isGzip()) {
        captureGzipResponse(response, directory, filename, isCanceled)
    } else {
        ResponseStorage.create(directory, filename).use { storage ->
            val preview = body.use { streamResponse(it.source(), storage, isCanceled) }
            storage.complete()
            val replay = storage.path.replayBody(body.contentType())
            ResponseCapture(
                response.newBuilder().body(replay).build(),
                previewResponse(response, preview),
                storage.path
            )
        }
    }
}

private fun captureGzipResponse(
    response: Response,
    directory: Path,
    filename: String,
    isCanceled: () -> Boolean
): ResponseCapture {
    val replayName = ".kttp-replay-${UUID.randomUUID()}.gz"
    return ResponseStorage.create(directory, replayName).use { raw ->
        response.body!!.use { body ->
            copyResponse(body.source(), raw.output, isCanceled)
        }
        raw.output.close()
        ResponseStorage.create(directory, filename).use { storage ->
            val preview = GzipSource(raw.path.source(NOFOLLOW_LINKS)).buffer().use {
                streamResponse(it, storage, isCanceled)
            }
            storage.complete()
            val replay = raw.path.replayBody(response.body!!.contentType(), deleteOnClose = true)
            raw.complete()
            ResponseCapture(
                response.newBuilder().body(replay).build(),
                previewResponse(response, preview),
                storage.path
            )
        }
    }
}

private fun streamResponse(
    source: BufferedSource,
    storage: ResponseStorage,
    isCanceled: () -> Boolean
): ByteArray {
    val preview = Buffer()
    copyResponse(source, storage.output, isCanceled) { buffer, count ->
        val remaining = MAX_RESPONSE_PREVIEW_BYTES - preview.size
        if (remaining > 0L) buffer.copyTo(preview, 0, minOf(count, remaining))
    }
    return preview.readByteArray()
}

private fun copyResponse(
    source: BufferedSource,
    output: java.io.OutputStream,
    isCanceled: () -> Boolean,
    onChunk: (Buffer, Long) -> Unit = { _, _ -> }
) {
    val chunk = Buffer()
    while (true) {
        if (isCanceled()) throw IOException("Canceled")
        val count = source.read(chunk, RESPONSE_COPY_BUFFER_BYTES)
        if (count == -1L) break
        onChunk(chunk, count)
        chunk.writeTo(output, count)
    }
    if (isCanceled()) throw IOException("Canceled")
}

private fun readPreview(response: Response, source: BufferedSource): ByteArray {
    val decoded = if (response.isGzip()) GzipSource(source).buffer() else source
    return decoded.use {
        val preview = Buffer()
        while (preview.size < MAX_RESPONSE_PREVIEW_BYTES) {
            if (it.read(preview, MAX_RESPONSE_PREVIEW_BYTES - preview.size) == -1L) break
        }
        preview.readByteArray()
    }
}

private fun previewResponse(response: Response, bytes: ByteArray): Response =
    response.newBuilder().body(bytes.toResponseBody(response.body?.contentType())).build()

private fun Response.isGzip() = "gzip".equals(header("Content-Encoding"), ignoreCase = true)
