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

/**
 * Immutable format-neutral import values. Parsers retain source text and never execute it.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
data class ImportInput(val text: String, val environmentText: String? = null, val baseDirectory: String? = null)

/**
 * Parsed requests and optional environment profiles, before any host writes.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
data class ImportBundle(
    val requests: List<ImportedRequest> = emptyList(),
    val variables: Map<String, String> = emptyMap(),
    val environments: List<ImportedEnvironment> = emptyList(),
    val diagnostics: List<ImportDiagnostic> = emptyList(),
    val sourceName: String? = null,
    val sourceFormat: ImportFormat? = null
)

/**
 * One request with ordered, possibly repeated HTTP fields and its origin.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
data class ImportedRequest(
    val name: String,
    val method: String,
    val url: ImportValue,
    val headers: List<ImportField> = emptyList(),
    val query: List<ImportField> = emptyList(),
    val auth: ImportAuth = ImportAuth.None,
    val body: ImportBody = ImportBody.None,
    val noRedirect: Boolean = false,
    val sourcePath: String = name,
    val variables: Map<String, String> = emptyMap(),
    val diagnostics: List<ImportDiagnostic> = emptyList()
) {
    val selectable: Boolean get() = diagnostics.none { it.severity == ImportSeverity.ERROR }
    val selectedByDefault: Boolean get() = selectable && diagnostics.none { it.requiresConsent }
}

/**
 * Literal text, or Postman text whose {{name}} references require conversion.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
data class ImportValue(val text: String, val interpolate: Boolean = false)

/**
 * An ordered header, query or form field.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
data class ImportField(val name: String, val value: ImportValue)

/**
 * An explicitly named environment; values are not applied until the user selects the profile.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
data class ImportedEnvironment(val name: String, val values: Map<String, String>)

/**
 * Static authentication representable by Kttp without executing authorization flows.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
sealed interface ImportAuth {
    /**
     * No explicit authentication is attached to the imported request.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    data object None : ImportAuth

    /**
     * Static HTTP Basic credentials.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    data class Basic(val username: ImportValue, val password: ImportValue) : ImportAuth

    /**
     * Static bearer token authentication.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    data class Bearer(val token: ImportValue) : ImportAuth

    /**
     * An API key sent as a header or query parameter.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    data class ApiKey(val key: String, val value: ImportValue, val inQuery: Boolean) : ImportAuth
}

/**
 * Body variants retain file references without reading or uploading local files.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
sealed interface ImportBody {
    /**
     * The request has no body.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    data object None : ImportBody

    /**
     * An exact text payload and optional media type.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    data class Text(val content: ImportValue, val contentType: String? = null) : ImportBody

    /**
     * Ordered URL-encoded form fields.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    data class Form(val fields: List<ImportField>) : ImportBody

    /**
     * Ordered multipart fields and file references.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    data class Multipart(val parts: List<ImportPart>) : ImportBody

    /**
     * A binary body read from its path only during execution.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    data class File(val path: ImportValue) : ImportBody
}

/**
 * A multipart text field or file reference with optional content type and filename.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
data class ImportPart(
    val name: String, val value: ImportValue, val file: Boolean = false,
    val contentType: String? = null, val filename: String? = null
)

/**
 * Diagnostics identify a source location; retained text may contain user-provided source data.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
data class ImportDiagnostic(
    val severity: ImportSeverity, val location: String, val message: String,
    val requiresConsent: Boolean = false, val retainedSource: String? = null
)

/**
 * Errors block an item, warnings require review, and information describes a conversion.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
enum class ImportSeverity { ERROR, WARNING, INFO }
