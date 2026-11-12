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

package io.github.photowey.kttp.idea.toolwindow

import com.intellij.openapi.vfs.VirtualFile
import io.github.photowey.kttp.core.enums.HttpMethod
import io.github.photowey.kttp.idea.gui.*

/**
 * Converts the Swing form at one boundary; the working session owns the resulting request.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal class RequestEditorFormMapper(
    private val header: ComposerHeaderPanel,
    private val params: ParamsTabPanel,
    private val headers: HeadersTabPanel,
    private val body: BodyTabPanel,
    private val auth: AuthTabPanel
) {
    fun fill(draft: GuiDraft) {
        val spec = draft.request
        header.executionModeCombo.selectedItem = draft.executionMode
        header.titleField.text = draft.title
        header.requestNameField.text = spec.identity.name.orEmpty()
        header.methodComboBox.model.selectedItem =
            if (draft.bridge.mode == GuiMode.SCRIPT_ONLY && draft.bridge.gaps.any { it.location == "method" })
                "Script" else spec.method.value
        header.urlField.text = GuiValueRefCodec.toDisplayValue(spec.target.url)
        params.setParamEntries(spec.target.queryParams.map { it.name to GuiValueRefCodec.toDisplayValue(it.value) })
        headers.setHeaderEntries(spec.headers.map { it.name to GuiValueRefCodec.toDisplayValue(it.value) })
        body.setBodySpec(spec.body)
        auth.setAuthSpec(spec.auth)
        header.noRedirectCheck.isSelected = spec.transport.noRedirect
        header.noCookiesCheck.isSelected = spec.transport.noCookies
        header.http2Check.isSelected = spec.transport.http2PriorKnowledge
    }

    fun read(draft: GuiDraft, source: VirtualFile?, index: Int?): GuiDraft {
        if (draft.ownership == DraftOwnership.SCRIPT_IMPORTED) return draft
        val spec = RequestSpec(
            identity = RequestIdentity(
                header.requestNameField.text.trim().takeIf(String::isNotBlank),
                source?.path,
                index
            ),
            method = HttpMethod.valueOf(header.methodComboBox.selectedItem as String),
            target = RequestTarget(
                GuiValueRefCodec.parse(header.urlField.text), draft.request.target.pathParams,
                params.getParamEntries().map { (name, value) -> NameValueRef(name, GuiValueRefCodec.parse(value)) }
            ),
            headers = headers.getHeaderEntries()
                .map { (name, value) -> HeaderSpec(name, GuiValueRefCodec.parse(value)) },
            auth = auth.getAuthSpec { GuiValueRefCodec.parse(it) },
            body = body.getBodySpec(),
            transport = TransportSpec(
                header.noRedirectCheck.isSelected,
                header.noCookiesCheck.isSelected,
                header.http2Check.isSelected
            )
        )
        val title = header.titleField.text.trim().ifBlank {
            "${spec.method.value} ${GuiValueRefCodec.toDisplayValue(spec.target.url).ifBlank { "request" }}".take(80)
        }
        return draft.copy(title = title, request = spec)
    }

    fun commitPendingEdits(): Boolean =
        params.commitPendingEdits() && headers.commitPendingEdits() && body.commitPendingEdits()
}
