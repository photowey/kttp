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

package io.github.photowey.kttp.idea.importing

import io.github.photowey.kttp.core.enums.HttpMethod
import io.github.photowey.kttp.idea.gui.*
import java.util.UUID

/**
 * Projects imports into existing GUI models; unsupported fields keep their original script semantics.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
object ImportRequestAdapter {
    fun adapt(request: ImportedRequest): GuiDraft? {
        if (request.method !in HttpMethod.entries.map { it.value } ||
            request.body is ImportBody.Multipart || request.body is ImportBody.File) return null
        val spec = specification(request)
        val values = mutableListOf(spec.target.url)
        values += spec.target.queryParams.map { it.value }
        values += spec.headers.map { it.value }
        when (val auth = spec.auth) {
            is AuthSpec.Basic -> values += listOf(auth.username, auth.password)
            is AuthSpec.Bearer -> values += auth.token
            is AuthSpec.ApiKey -> values += auth.keyValue
            AuthSpec.Inherit -> Unit
        }
        when (val body = spec.body) {
            is BodySpec.Raw -> values += body.content
            is BodySpec.Json -> values += body.content
            is BodySpec.FormUrlEncoded -> values += body.fields.map { it.value }
            BodySpec.None -> Unit
        }
        if (values.any { it is ValueRef.OpaqueScript }) return null
        return GuiDraft(UUID.randomUUID().toString(), request.name, DraftOwnership.GUI_OWNED, spec)
    }

    internal fun specification(request: ImportedRequest): RequestSpec {
        fun value(v: ImportValue) = ImportValues.resolve(v, request.variables)
        val auth = when (val a = request.auth) {
            ImportAuth.None -> AuthSpec.Inherit
            is ImportAuth.Basic -> AuthSpec.Basic(value(a.username), value(a.password))
            is ImportAuth.Bearer -> AuthSpec.Bearer(value(a.token))
            is ImportAuth.ApiKey -> AuthSpec.ApiKey(
                a.key,
                value(a.value),
                if (a.inQuery) ApiKeyPlacement.QUERY else ApiKeyPlacement.HEADER
            )
        }
        val body = when (val b = request.body) {
            ImportBody.None, is ImportBody.File, is ImportBody.Multipart -> BodySpec.None
            is ImportBody.Text -> BodySpec.Raw(
                value(b.content),
                b.contentType?.takeIf { request.headers.none { header -> header.name.equals("Content-Type", true) } }
                    ?.let { ValueRef.Literal(it) })

            is ImportBody.Form -> BodySpec.FormUrlEncoded(b.fields.map { NameValueRef(it.name, value(it.value)) })
        }
        return RequestSpec(
            identity = RequestIdentity(name = request.name),
            method = HttpMethod.entries.firstOrNull { it.value == request.method } ?: HttpMethod.GET,
            target = RequestTarget(
                value(request.url),
                queryParams = request.query.map { NameValueRef(it.name, value(it.value)) }),
            headers = request.headers.map { HeaderSpec(it.name, value(it.value)) }, auth = auth, body = body,
            transport = TransportSpec(noRedirect = request.noRedirect)
        )
    }
}
