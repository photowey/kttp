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

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Exercises static cURL conversion without executing commands or reading request files.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
class CurlImportParserTest {
    /**
     * Retains URL encoding and repeated pairs, including a literal plus.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun preservesOriginalQueryEncoding() {
        val request = request("curl 'https://example.com/a?x=a%2Fb&x=a+b&empty&x=%25#part'")
        assertEquals("https://example.com/a?x=a%2Fb&x=a+b&empty&x=%25#part", request.url.text)
        assertTrue(request.query.isEmpty())
        assertFalse(request.url.interpolate)
        assertTrue(request.noRedirect)
    }

    /**
     * Browser headers stay ordered and duplicated instead of being placed in a map.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun preservesRepeatedHeadersAndQuotedDollars() {
        val request = request("curl 'https://example.com' -H 'X-Value: \$TOKEN' -H 'X-Value: a:b' -H 'X-Empty;'")
        assertEquals(listOf("X-Value", "X-Value", "X-Empty"), request.headers.map { it.name })
        assertEquals(listOf("\$TOKEN", "a:b", ""), request.headers.map { it.value.text })
        assertTrue(request.headers.none { it.value.interpolate })
    }

    /**
     * Handles shell concatenation, escaped quotes and backslash line continuation.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun tokenizesPosixQuotingAndContinuation() {
        val command = "curl \\\n  'https://example.com' \\\n  -H \"X-Value: a\\\"b\\\\c\\\$d\" --data-raw 'one'\\''two'"
        val request = request(command)
        assertEquals("a\"b\\c\$d", request.headers.single().value.text)
        assertEquals("one'two", assertIs<ImportBody.Text>(request.body).content.text)
    }

    /**
     * Explicit methods, HEAD and redirect switches are respected.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun respectsCustomMethodHeadAndRedirects() {
        assertEquals("HEAD", request("curl -I https://example.com").method)
        val custom = request("curl -LX PROPFIND https://example.com")
        assertEquals("PROPFIND", custom.method)
        assertFalse(custom.noRedirect)
        assertTrue(request("curl -L --no-location https://example.com").noRedirect)
    }

    /**
     * Basic credentials split only on the first colon and bearer values remain literal.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun importsStaticAuthenticationAndCookies() {
        val basic = request("curl -u 'user:pass:word' -b 'sid=a; lang=en' https://example.com")
        assertEquals(ImportAuth.Basic(ImportValue("user"), ImportValue("pass:word")), basic.auth)
        assertEquals(ImportField("Cookie", ImportValue("sid=a; lang=en")), basic.headers.single())
        assertEquals(
            ImportAuth.Bearer(ImportValue("abc.def")),
            request("curl --oauth2-bearer abc.def https://example.com").auth
        )
    }

    /**
     * Repeated data arguments join with ampersands without decoding existing escapes.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun preservesDataWireText() {
        val request = request("curl --data 'a=a%2Fb' --data-raw '@literal+value' https://example.com")
        assertEquals("POST", request.method)
        assertEquals(
            ImportBody.Text(ImportValue("a=a%2Fb&@literal+value"), "application/x-www-form-urlencoded"),
            request.body
        )
    }

    /**
     * The JSON option concatenates payloads and supplies overridable JSON headers.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun importsJsonDefaultsAndOverrides() {
        val request = request("curl --json '{\"a\":' --json '1}' -H 'Accept: custom/type' https://example.com")
        assertEquals("{\"a\":1}", assertIs<ImportBody.Text>(request.body).content.text)
        assertEquals("application/json", request.headers.single { it.name == "Content-Type" }.value.text)
        assertEquals("custom/type", request.headers.single { it.name == "Accept" }.value.text)
    }

    /**
     * URLencoded data adds exact escaped query bytes before an existing fragment.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun getAppendsEncodedDataWithoutChangingOriginalUrl() {
        val request =
            request("curl -G 'https://example.com?a=%2F#frag' --data-urlencode 'q=a b+c' --data-urlencode 'q=x/y'")
        assertEquals("GET", request.method)
        assertEquals("https://example.com?a=%2F&q=a%20b%2Bc&q=x%2Fy#frag", request.url.text)
        assertEquals(ImportBody.None, request.body)
        assertTrue(request.query.isEmpty())
    }

    /**
     * Named URLencoded fields retain curl's already-escaped field names.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun urlencodedBodyPreservesFieldNamesAndUtf8Encoding() {
        val request = request("curl --data-urlencode 'a%20b=你好 +' --data-urlencode '=raw value' https://example.com")
        assertEquals("a%20b=%E4%BD%A0%E5%A5%BD%20%2B&raw%20value", assertIs<ImportBody.Text>(request.body).content.text)
    }

    /**
     * Body files stay references and are resolved without requiring file existence.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun importsBinaryFileReferenceWithoutReadingIt() {
        val request = CurlImportParser().parse(
            ImportInput(
                "curl --data-binary '@missing.bin' https://example.com",
                baseDirectory = "/imports"
            )
        ).requests.single()
        assertEquals(ImportBody.File(ImportValue("/imports/missing.bin")), request.body)
        assertEquals(
            "application/x-www-form-urlencoded",
            request.headers.single { it.name == "Content-Type" }.value.text
        )
        assertTrue(request.selectable)
    }

    /**
     * Every repeated data argument retains its separator, including empty values.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun preservesEmptyDataArguments() {
        val request = request("curl -d '' -d 'a=b' -d '' https://example.com")
        assertEquals("&a=b&", assertIs<ImportBody.Text>(request.body).content.text)
    }

    /**
     * GET moves the complete joined data to the query without dropping empty arguments.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun preservesEmptyGetDataArguments() {
        val request = request("curl -G -d '' -d 'a=b' -d '' https://example.com/path#fragment")
        assertEquals("https://example.com/path?&a=b&#fragment", request.url.text)
        assertEquals(ImportBody.None, request.body)
    }

    /**
     * A custom body content type remains consistent with its explicit HTTP header.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun honorsExplicitBodyContentType() {
        val request = request("curl -d '{\"a\":1}' -H 'Content-Type: application/json' https://example.com")
        assertEquals("application/json", assertIs<ImportBody.Text>(request.body).contentType)
    }

    /**
     * A custom method pinned across redirects cannot be silently rewritten by the target.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun rejectsPinnedCustomPostRedirects() {
        assertBlocked("curl -L -X POST -d a=b https://example.com")
        assertBlocked("curl -L -X PUT -d a=b https://example.com")
    }

    /**
     * Authentication disabling and mixed cookie sources must not be silently ignored.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun rejectsAmbiguousAuthenticationAndCookies() {
        assertBlocked("curl -u user:password --no-basic https://example.com")
        assertBlocked("curl -b a=b -b c=d https://example.com")
        assertBlocked("curl -b cookies.txt -H 'Cookie: a=b' https://example.com")
    }

    /**
     * Shell filename expansion and escaped NUL input are unsupported command syntax.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun rejectsShellGlobsAndEscapedNul() {
        assertBlocked("curl https://example.com/a* -d value")
        assertBlocked("curl https://example.com --data-raw \\\u0000")
    }

    /**
     * Raw GET data cannot introduce an unintended URL fragment or illegal whitespace.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun rejectsAmbiguousRawQueryData() {
        assertBlocked("curl -G -d 'q=a#b' https://example.com")
        assertBlocked("curl -G -d 'q=a b' https://example.com")
        assertBlocked("curl -G -d 'q=%invalid' https://example.com")
    }

    /**
     * Multipart supports file metadata, repeated files and literal form strings.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun importsMultipartFieldsAndFileMetadata() {
        val request =
            request("curl -F 'title=hello' -F 'upload=@\"a,b.txt\";type=text/plain;filename=\"a;b.txt\"' --form-string 'literal=@file;type=text/plain' https://example.com")
        val parts = assertIs<ImportBody.Multipart>(request.body).parts
        assertEquals(ImportPart("title", ImportValue("hello")), parts[0])
        assertEquals(ImportPart("upload", ImportValue("a,b.txt"), true, "text/plain", "a;b.txt"), parts[1])
        assertEquals("@file;type=text/plain", parts[2].value.text)
        assertFalse(parts[2].file)
    }

    /**
     * File parts always carry curl's binary default without inspecting local content.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun defaultsMultipartFileContentTypeToOctetStream() {
        val request = request("curl -F 'upload=@/missing/no-extension' https://example.com")
        val part = assertIs<ImportBody.Multipart>(request.body).parts.single()
        assertEquals("application/octet-stream", part.contentType)
        assertTrue(request.selectedByDefault)
    }

    /**
     * Curl's MIME suffix table is matched case insensitively.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun infersKnownMultipartFileTypes() {
        val types = mapOf(
            "gif" to "image/gif", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
            "svg" to "image/svg+xml", "txt" to "text/plain", "htm" to "text/html", "html" to "text/html",
            "pdf" to "application/pdf", "xml" to "application/xml"
        )
        for ((extension, type) in types) {
            val request = request("curl -F 'upload=@/missing/file.${extension.uppercase()}' https://example.com")
            assertEquals(type, assertIs<ImportBody.Multipart>(request.body).parts.single().contentType)
            assertTrue(request.selectedByDefault)
        }
    }

    /**
     * The posted filename is checked before the local filename, unless type is explicit.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun respectsMultipartFilenameAndExplicitType() {
        val remote = request("curl -F 'upload=@file.txt;filename=photo.jpg' https://example.com")
        assertEquals("image/jpeg", assertIs<ImportBody.Multipart>(remote.body).parts.single().contentType)
        val fallback = request("curl -F 'upload=@file.pdf;filename=download.custom' https://example.com")
        assertEquals("application/pdf", assertIs<ImportBody.Multipart>(fallback.body).parts.single().contentType)
        val explicit = request("curl -F 'upload=@file.jpg;type=application/custom' https://example.com")
        assertEquals("application/custom", assertIs<ImportBody.Multipart>(explicit.body).parts.single().contentType)
    }

    /**
     * Unknown suffixes use a binary fallback and require review rather than hidden guessing.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun requiresReviewForUnknownMultipartFileTypes() {
        val request = request("curl -F 'upload=@file.unknown' https://example.com")
        assertEquals(
            "application/octet-stream",
            assertIs<ImportBody.Multipart>(request.body).parts.single().contentType
        )
        assertTrue(request.diagnostics.any { it.severity == ImportSeverity.WARNING && it.requiresConsent })
        assertFalse(request.selectedByDefault)
    }

    /**
     * Literal cURL braces cannot become executable Kttp path-parameter syntax.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun rejectsLiteralUrlBracesEvenWhenGlobbingIsDisabled() {
        assertBlocked("curl -g 'https://example.com/{id}'")
        assertBlocked("curl -g 'https://example.com/?value={id}'")
        assertBlocked("curl -G -g -d 'value={id}' https://example.com")
        val encoded = request("curl -g 'https://example.com/%7Bid%7D'")
        assertEquals("https://example.com/%7Bid%7D", encoded.url.text)
    }

    /**
     * Every unrepresentable option or shell program blocks static import.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun rejectsUnknownFlagsAndShellExecution() {
        listOf(
            "curl --unknown value https://example.com",
            "curl https://example.com | sh",
            "curl https://example.com; echo hello",
            "curl \"https://example.com/\$(whoami)\"",
            "curl `whoami` https://example.com",
            "curl https://example.com > response.txt",
            "curl https://example.com && curl https://other.example.com",
            "curl \$URL",
            "curl --data \$'hello\\n' https://example.com"
        ).forEach(::assertBlocked)
    }

    /**
     * Input failures are diagnostics instead of exceptions or executable requests.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun rejectsMalformedAndAmbiguousInputs() {
        listOf(
            "", "echo https://example.com", "curl", "curl -H", "curl 'https://example.com",
            "curl https://example.com https://other.example.com", "curl ftp://example.com",
            "curl -I -d text https://example.com", "curl -F a=b -d c=d https://example.com",
            "curl -u missingpassword https://example.com", "curl -b cookies.txt https://example.com",
            "curl --data @text.txt https://example.com", "curl --data-urlencode name@text.txt https://example.com",
            "curl --data-binary @- https://example.com", "curl -H @headers.txt https://example.com",
            "curl -F 'a=<file.txt' https://example.com", "curl -F 'a=@file;encoder=base64' https://example.com",
            "curl 'https://example.com/{a,b}'", "curl https://example.com\ncurl https://other.example.com"
        ).forEach(::assertBlocked)
    }

    /**
     * Header suppression is surfaced because the target client has different defaults.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun rejectsHeaderSuppressionRatherThanSendingAnEmptyHeader() {
        assertBlocked("curl -H 'Accept:' https://example.com")
    }

    /**
     * Credential values must not appear in diagnostic summaries.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    @Test
    fun errorsDoNotEchoSecretsOrSourceCommands() {
        val bundle =
            CurlImportParser().parse(ImportInput("curl -u 'secret-user:secret-password' --bad-option https://example.com"))
        assertTrue(bundle.diagnostics.isNotEmpty())
        assertTrue(bundle.diagnostics.none { it.message.contains("secret-") })
        assertTrue(bundle.requests.flatMap { it.diagnostics }.none { it.message.contains("secret-") })
    }

    /**
     * Parses a valid single request and catches accidental blocking diagnostics.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun request(command: String): ImportedRequest {
        val bundle = CurlImportParser().parse(ImportInput(command))
        assertTrue(bundle.diagnostics.none { it.severity == ImportSeverity.ERROR }, bundle.diagnostics.toString())
        return bundle.requests.single().also { assertTrue(it.selectable, it.diagnostics.toString()) }
    }

    /**
     * A malformed command cannot yield an executable selected request.
     *
     * @author photowey
     * @version 1.2.0
     * @since 2026/11/13
     */
    private fun assertBlocked(command: String) {
        val bundle = CurlImportParser().parse(ImportInput(command))
        assertTrue(
            bundle.diagnostics.any { it.severity == ImportSeverity.ERROR } || bundle.requests.any { !it.selectable },
            command
        )
        assertTrue(bundle.requests.none { it.selectedByDefault }, command)
    }
}
