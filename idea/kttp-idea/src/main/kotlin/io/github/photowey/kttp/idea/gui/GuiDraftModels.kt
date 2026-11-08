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

package io.github.photowey.kttp.idea.gui

import io.github.photowey.kttp.core.enums.HttpMethod
import io.github.photowey.kttp.idea.execution.RequestExecutionMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Stores a GUI request draft, its ownership, script association, and execution preference.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
@Serializable
data class GuiDraft(
    val id: String,
    val title: String,
    val ownership: DraftOwnership,
    val request: RequestSpec,
    val bridge: ScriptBridgeState = ScriptBridgeState(),
    val meta: DraftMeta = DraftMeta(),
    val executionMode: RequestExecutionMode = RequestExecutionMode.QUICK_REQUEST
)

/**
 * Identifies whether a draft is GUI-owned, imported from a script, or detached.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
@Serializable
enum class DraftOwnership {
    GUI_OWNED,
    SCRIPT_IMPORTED,
    DETACHED
}

/**
 * Tracks unsaved state and the draft's latest generation and execution metadata.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
@Serializable
data class DraftMeta(
    val dirty: Boolean = false,
    val lastSentAt: Long? = null,
    val lastGeneratedAt: Long? = null,
    val lastResultRef: String? = null
)

/**
 * Tracks source and generated script associations together with projection capabilities and gaps.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
@Serializable
data class ScriptBridgeState(
    val sourceScriptPath: String? = null,
    val mode: GuiMode = GuiMode.GUI_READY,
    val gaps: List<SemanticGap> = emptyList(),
    val generatedScriptPath: String? = null,
    val generatedScriptFingerprint: String? = null
)

/**
 * Classifies how much of a script request the GUI can represent and generate.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
@Serializable
enum class GuiMode {
    GUI_READY,
    SEND_ONLY,
    SCRIPT_ONLY
}

/**
 * Describes a script semantic feature that cannot be fully represented by the GUI projection.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
@Serializable
data class SemanticGap(
    val kind: GapKind,
    val location: String? = null,
    val message: String
)

/**
 * Categorizes projection limitations used to determine GUI capabilities.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
@Serializable
enum class GapKind {
    POST_PROCESSING_ONLY,
    REQUEST_SHAPING_UNSUPPORTED,
    SCRIPT_EXPRESSION_UNSUPPORTED,
    SCRIPT_ONLY_FEATURE,
    PARTIALLY_PROJECTED
}

/**
 * Describes the HTTP request values, authentication, body, and transport options represented by the GUI.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
@Serializable
data class RequestSpec(
    val identity: RequestIdentity = RequestIdentity(),
    val method: HttpMethod = HttpMethod.GET,
    val target: RequestTarget = RequestTarget(ValueRef.Literal("")),
    val headers: List<HeaderSpec> = emptyList(),
    val auth: AuthSpec = AuthSpec.Inherit,
    val body: BodySpec = BodySpec.None,
    val transport: TransportSpec = TransportSpec()
)

/**
 * Associates a request with its name and optional source-file declaration.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
@Serializable
data class RequestIdentity(
    val name: String? = null,
    val sourceFile: String? = null,
    val declarationIndex: Int? = null
)

/**
 * Contains the URL value and ordered path and query parameters.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
@Serializable
data class RequestTarget(
    val url: ValueRef,
    val pathParams: List<NameValueRef> = emptyList(),
    val queryParams: List<NameValueRef> = emptyList()
)

/**
 * Pairs a parameter name with a value source.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
@Serializable
data class NameValueRef(
    val name: String,
    val value: ValueRef
)

/**
 * Pairs an HTTP header name with a value source.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
@Serializable
data class HeaderSpec(
    val name: String,
    val value: ValueRef
)

/**
 * Identifies where a request field obtains its value.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
@Serializable
sealed class ValueRef {
    /**
     * Carries a value that should be used as literal text.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    @Serializable
    @SerialName("literal")
    data class Literal(val value: String) : ValueRef()

    /**
     * References a key in the selected script environment.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    @Serializable
    @SerialName("env")
    data class Env(val key: String) : ValueRef()

    /**
     * References local-file content with its encoding and required-file setting.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    @Serializable
    @SerialName("localFile")
    data class LocalFile(
        val path: String,
        val charset: String = "UTF-8",
        val required: Boolean = true
    ) : ValueRef()

    /**
     * Preserves Kotlin source that the GUI cannot resolve as a direct request value.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    @Serializable
    @SerialName("opaqueScript")
    data class OpaqueScript(val raw: String) : ValueRef()
}

/**
 * Describes the authentication options represented by a GUI request.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
@Serializable
sealed class AuthSpec {
    /**
     * Leaves authentication to the request's existing headers or execution context.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    @Serializable
    @SerialName("inherit")
    data object Inherit : AuthSpec()

    /**
     * Supplies a Bearer authorization token from a value source.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    @Serializable
    @SerialName("bearer")
    data class Bearer(val token: ValueRef) : AuthSpec()

    /**
     * Supplies a Basic authentication username and password from value sources.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    @Serializable
    @SerialName("basic")
    data class Basic(val username: ValueRef, val password: ValueRef) : AuthSpec()

    /**
     * Supplies an API key and selects its header or query placement.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    @Serializable
    @SerialName("apiKey")
    data class ApiKey(
        val keyName: String,
        val keyValue: ValueRef,
        val placement: ApiKeyPlacement = ApiKeyPlacement.HEADER
    ) : AuthSpec()
}

/**
 * Selects whether an API key is sent as a header or query parameter.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
@Serializable
enum class ApiKeyPlacement {
    HEADER,
    QUERY
}

/**
 * Describes the HTTP body representations supported by the GUI request model.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
@Serializable
sealed class BodySpec {
    /**
     * Represents a request without a body.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    @Serializable
    @SerialName("none")
    data object None : BodySpec()

    /**
     * Carries JSON body content as a value source.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    @Serializable
    @SerialName("json")
    data class Json(val content: ValueRef) : BodySpec()

    /**
     * Carries raw body content and an optional explicit content type.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    @Serializable
    @SerialName("raw")
    data class Raw(
        val content: ValueRef,
        val contentType: ValueRef? = null
    ) : BodySpec()

    /**
     * Carries ordered name-value fields for an URL-encoded form body.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    @Serializable
    @SerialName("form")
    data class FormUrlEncoded(val fields: List<NameValueRef>) : BodySpec()
}

/**
 * Records redirect, cookie, and HTTP/2 prior-knowledge options for a GUI request.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
@Serializable
data class TransportSpec(
    val noRedirect: Boolean = false,
    val noCookies: Boolean = false,
    val http2PriorKnowledge: Boolean = false
)
