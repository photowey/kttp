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

import java.net.URI
import java.net.URISyntaxException

/**
 * Parses a bounded POSIX cURL command without executing commands or requests.
 *
 * Supports HTTP(S), static headers/authentication, data and multipart file references.
 * Unknown options, shell expansion, multiple transfers and unrepresentable file
 * transformations are errors. URL/data escaping is retained as wire text.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
class CurlImportParser : RequestImportParser {
    /**
     * Converts one command or returns safe diagnostics; no files or shell are accessed.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    override fun parse(input: ImportInput): ImportBundle = try {
        val options = CurlOptions(CurlLexer(input.text).tokenize()).parse()
        val request = CurlRequestBuilder(options, input.baseDirectory).build()
        ImportBundle(requests = listOf(request), diagnostics = request.diagnostics)
    } catch (failure: CurlParseFailure) {
        ImportBundle(
            diagnostics = listOf(
                ImportDiagnostic(
                    ImportSeverity.ERROR,
                    "cURL:character ${failure.offset + 1}",
                    failure.message ?: "Invalid cURL command."
                )
            )
        )
    }
}

/**
 * Assembles static HTTP semantics independently of the host's GUI or scripting APIs.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
private class CurlRequestBuilder(private val options: List<CurlOption>, private val baseDirectory: String?) {
    private val diagnostics = mutableListOf<ImportDiagnostic>()

    /**
     * Builds one request, preserving ordering and the default no-follow redirect behavior.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    fun build(): ImportedRequest {
        val urls = options.filter { it.name == "--url" }
        if (urls.size != 1) fail(urls.lastOrNull(), "Import requires exactly one HTTP(S) URL and one transfer.")
        val originalUrl = urls.single()
        validateUrl(originalUrl)
        val get = enabled("--get", "--no-get")
        val head = enabled("--head", "--no-head")
        val body = CurlBodyParser(baseDirectory, diagnostics).parse(options, get, head)
        val headers = headers(body.json).toMutableList()
        if (body.body is ImportBody.File && headers.none { it.name.equals("Content-Type", true) }) {
            headers += ImportField("Content-Type", ImportValue("application/x-www-form-urlencoded"))
        }
        val auth = auth(headers)
        val method = method(body.body, get, head)
        val follow = enabled("--location", "--no-location")
        val customMethod = options.lastOrNull { it.name == "--request" }
        if (follow && customMethod != null && method !in setOf("GET", "HEAD", "PROPFIND")) {
            fail(
                customMethod,
                "Following redirects with this pinned custom method cannot preserve curl's method behavior."
            )
        }
        describeNonRequestOptions()
        if (follow && headers.any { it.name.equals("Cookie", true) }) {
            fail(
                options.last { it.name == "--location" },
                "Following redirects with an explicit Cookie header requires manual review of cross-host cookie handling."
            )
        }
        val url = body.queryData?.let { appendQuery(originalUrl, it) } ?: originalUrl.value!!
        val adjustedBody = if (body.body is ImportBody.Text) {
            body.body.copy(
                contentType = headers.lastOrNull { it.name.equals("Content-Type", true) }?.value?.text
                    ?: body.body.contentType
            )
        } else body.body
        return ImportedRequest(
            name = "Imported cURL request",
            method = method,
            url = ImportValue(url),
            headers = headers,
            auth = auth,
            body = adjustedBody,
            noRedirect = !follow,
            sourcePath = "cURL",
            diagnostics = diagnostics.toList()
        )
    }

    /**
     * A custom method is retained verbatim and never collapsed to the GUI method enum.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun method(body: ImportBody, get: Boolean, head: Boolean): String {
        val explicit = options.lastOrNull { it.name == "--request" }
        if (explicit != null) {
            if (!httpToken(explicit.value!!)) fail(explicit, "The custom HTTP method is invalid.")
            return explicit.value
        }
        return when {
            head -> "HEAD"
            get || body == ImportBody.None -> "GET"
            else -> "POST"
        }
    }

    /**
     * Requires explicit HTTP(S) and prevents curl URL globbing from becoming one request.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun validateUrl(option: CurlOption) {
        val value = option.value!!
        if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) {
            fail(option, "Only explicit http:// or https:// URLs can be imported.")
        }
        rejectLiteralBraces(option, value)
        val globoff = enabled("--globoff", "--no-globoff")
        val afterAuthority = value.substringAfter("://").dropWhile { it !in "/?#" }
        if (!globoff && afterAuthority.any { it in "{}[]" }) fail(
            option,
            "URL glob expansion is not supported; use --globoff for a literal URL."
        )
        try {
            val uri = URI(value)
            if (uri.rawAuthority.isNullOrEmpty() || value.any { it.isWhitespace() || it.isISOControl() }) fail(
                option,
                "The HTTP URL is malformed."
            )
            if (uri.rawUserInfo != null) fail(
                option,
                "Credentials embedded in a URL require an explicit --user option during import."
            )
        } catch (_: URISyntaxException) {
            fail(option, "The HTTP URL is malformed or contains invalid escaping.")
        }
    }

    /**
     * Keeps duplicate explicit headers and inserts only absent JSON defaults.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun headers(json: Boolean): List<ImportField> {
        val result = options.filter { it.name == "--header" }.map(::header).toMutableList()
        for ((optionName, headerName) in listOf("--user-agent" to "User-Agent", "--referer" to "Referer")) {
            val option = options.lastOrNull { it.name == optionName } ?: continue
            val value = option.value!!
            if (value.isEmpty() || '\r' in value || '\n' in value || (optionName == "--referer" && value.endsWith(";auto"))) {
                fail(option, "Empty, multiline or dynamic header shortcuts require manual conversion.")
            }
            if (result.none { it.name.equals(headerName, true) }) result += ImportField(headerName, ImportValue(value))
        }
        val cookies = options.filter { it.name == "--cookie" }
        if (cookies.size > 1) fail(
            cookies.last(),
            "Multiple cookie sources require manual conversion into one static cookie string."
        )
        val cookie = cookies.singleOrNull()
        if (cookie != null) {
            if ('=' !in cookie.value!! || '\n' in cookie.value || '\r' in cookie.value) fail(
                cookie,
                "Cookie files, empty cookie engines and multiline cookies cannot be imported; use a static name=value string."
            )
            if (result.none { it.name.equals("Cookie", true) }) result += ImportField(
                "Cookie",
                ImportValue(cookie.value)
            )
        }
        if (json) {
            for (name in listOf("Content-Type", "Accept")) {
                if (result.none { it.name.equals(name, true) }) result += ImportField(
                    name,
                    ImportValue("application/json")
                )
            }
        }
        return result
    }

    /**
     * Curl's name: suppression and name; empty-header syntax have different meanings.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun header(option: CurlOption): ImportField {
        val value = option.value!!
        if (value.startsWith('@')) fail(option, "Reading request headers from a file is not supported.")
        if ('\r' in value || '\n' in value) fail(option, "Multiline request headers cannot be imported.")
        val colon = value.indexOf(':')
        if (colon < 0) {
            val name = value.removeSuffix(";")
            if (!value.endsWith(';') || !httpToken(name)) fail(
                option,
                "A header requires a valid name and ':' or trailing ';'."
            )
            return ImportField(name, ImportValue(""))
        }
        val name = value.substring(0, colon)
        if (!httpToken(name)) fail(option, "The request header name is invalid.")
        val content = value.substring(colon + 1).trimStart(' ', '\t')
        if (content.isEmpty()) fail(
            option,
            "cURL header suppression cannot be represented as an empty header; remove or replace the suppression explicitly."
        )
        return ImportField(name, ImportValue(content))
    }

    /**
     * Password prompts and competing authentication mechanisms cannot be converted statically.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun auth(headers: List<ImportField>): ImportAuth {
        val basic = options.lastOrNull { it.name == "--user" }
        val bearer = options.lastOrNull { it.name == "--oauth2-bearer" }
        if (basic != null && bearer != null) fail(
            bearer,
            "Combining Basic and Bearer authentication requires manual conversion."
        )
        if (bearer != null && options.any { it.name == "--basic" }) fail(
            bearer,
            "Combining Basic selection with Bearer authentication requires manual conversion."
        )
        if (basic == null && bearer == null) return ImportAuth.None
        if (headers.any { it.name.equals("Authorization", true) }) {
            fail(
                basic ?: bearer,
                "Combining explicit Authorization headers with authentication options requires manual conversion."
            )
        }
        if (bearer != null) {
            if (bearer.value.isNullOrEmpty()) fail(bearer, "A static Bearer token is required.")
            return ImportAuth.Bearer(ImportValue(bearer.value))
        }
        val value = basic!!.value!!
        if (':' !in value) fail(
            basic,
            "Interactive password prompts are not supported; provide an explicit username:password value."
        )
        return ImportAuth.Basic(ImportValue(value.substringBefore(':')), ImportValue(value.substringAfter(':')))
    }

    /**
     * Response presentation flags need no request changes; compression differences require consent.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun describeNonRequestOptions() {
        if (enabled("--compressed", "--no-compressed")) {
            val option = options.last { it.name == "--compressed" }
            diagnostics += ImportDiagnostic(
                ImportSeverity.WARNING,
                "cURL:character ${option.offset + 1}",
                "--compressed uses the target HTTP client's compression support; curl's advertised algorithms are not reproduced.",
                requiresConsent = true
            )
        }
        val presentation = options.filter { it.name in presentationOptions }
        if (presentation.isNotEmpty()) diagnostics += ImportDiagnostic(
            ImportSeverity.INFO,
            "cURL",
            "Response display, output and curl startup options are omitted; import creates a request without running curl."
        )
    }

    /**
     * Appends query wire text before a fragment without decoding or re-encoding existing pairs.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun appendQuery(option: CurlOption, data: String): String {
        val url = option.value!!
        if (data.isEmpty()) return url
        rejectLiteralBraces(option, data)
        if (data.any { it == '#' || it.isWhitespace() || it.isISOControl() }) {
            fail(
                option,
                "Raw --get data contains ambiguous URL characters; use --data-urlencode or an explicitly encoded URL."
            )
        }
        val fragmentIndex = url.indexOf('#')
        val base = if (fragmentIndex < 0) url else url.substring(0, fragmentIndex)
        val fragment = if (fragmentIndex < 0) "" else url.substring(fragmentIndex)
        val combined = base + (if ('?' in base) "&" else "?") + data + fragment
        try {
            URI(combined)
        } catch (_: URISyntaxException) {
            fail(option, "Raw --get data contains invalid URL escaping; use --data-urlencode.")
        }
        return combined
    }

    /**
     * Stops a literal URL value from being interpreted as Kttp path-parameter syntax.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun rejectLiteralBraces(option: CurlOption, value: String) {
        if ('{' in value || '}' in value) fail(
            option,
            "Literal URL braces are unsupported because Kttp treats them as path parameters; use an explicitly percent-encoded URL when intended."
        )
    }

    /**
     * Repeated positive/negative switches follow curl's last-value behavior.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun enabled(positive: String, negative: String): Boolean =
        options.lastOrNull { it.name == positive || it.name == negative }?.name == positive

    /**
     * Uses the HTTP token alphabet for custom methods and field names.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun httpToken(value: String): Boolean = value.isNotEmpty() && value.all {
        it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "!#\$%&'*+-.^_`|~"
    }

    /**
     * Throws diagnostics with an option location, never with request or credential text.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun fail(option: CurlOption?, message: String): Nothing =
        throw CurlParseFailure(option?.offset ?: 0, message)

    /**
     * Options affecting curl's console/files, rather than the imported HTTP message.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private companion object {
        val presentationOptions = setOf(
            "--silent", "--no-silent", "--show-error", "--no-show-error", "--verbose", "--no-verbose",
            "--include", "--no-include", "--show-headers", "--no-show-headers", "--fail", "--no-fail",
            "--fail-with-body", "--no-fail-with-body", "--no-progress-meter", "--progress-meter", "--progress-bar",
            "--no-progress-bar", "--output", "--dump-header", "--write-out", "--disable"
        )
    }
}
