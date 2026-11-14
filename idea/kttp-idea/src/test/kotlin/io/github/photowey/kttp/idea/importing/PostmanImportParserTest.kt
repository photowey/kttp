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
 * Exercises collection semantics without invoking Postman, HTTP or filesystem access.
 *
 * @author photowey
 * @version 1.2.0
 * @since 2026/11/13
 */
class PostmanImportParserTest {
    @Test
    fun nestedRequestsRetainOrderAndNearestAuthentication() {
        val bundle = collection(
            """
            {"name":"Top","request":{"method":"GET","url":"https://example.test/top"}},
            {"name":"Folder","auth":{"type":"bearer","bearer":[{"key":"token","value":"folder"}]},"item":[
              {"name":"Inherited","request":{"method":"GET","url":"https://example.test/a"}},
              {"name":"Inner","item":[{"name":"Anonymous","request":{"method":"PATCH","url":"https://example.test/b","auth":{"type":"noauth"}}}]},
              {"name":"Own","request":{"method":"GET","url":"https://example.test/c","auth":{"type":"basic","basic":[{"key":"username","value":"u"},{"key":"password","value":"p"}]}}}
            ]}
        """, """"auth":{"type":"bearer","bearer":[{"key":"token","value":"collection"}]}"""
        )
        assertEquals(listOf("Top", "Inherited", "Anonymous", "Own"), bundle.requests.map { it.name })
        assertEquals("Demo/Folder/Inner/Anonymous", bundle.requests[2].sourcePath)
        assertEquals(ImportAuth.Bearer(ImportValue("collection")), bundle.requests[0].auth)
        assertEquals(ImportAuth.Bearer(ImportValue("folder")), bundle.requests[1].auth)
        assertEquals(ImportAuth.None, bundle.requests[2].auth)
        assertEquals(ImportAuth.Basic(ImportValue("u"), ImportValue("p")), bundle.requests[3].auth)
    }

    @Test
    fun structuredRawUrlKeepsEncodingAndOmitsDisabledQueriesWithoutDuplicatingThem() {
        val request = collection(
            """
            {"name":"Encoded","request":{"method":"GET","url":{
              "raw":"https://example.test/a%2Fb?enabled=a%2Bb&disabled=secret#part",
              "protocol":"https","host":["example","test"],"path":["a%2Fb"],
              "query":[{"key":"enabled","value":"a+b"},{"key":"disabled","value":"secret","disabled":true}]
            },"header":[{"key":"X-One","value":"1"},{"key":"X-One","value":"2"},{"key":"X-Off","value":"off","disabled":true}]}}
        """
        ).requests.single()
        assertEquals("https://example.test/a%2Fb?enabled=a%2Bb#part", request.url.text)
        assertTrue(request.query.isEmpty())
        assertEquals(listOf("X-One", "X-One"), request.headers.map { it.name })
    }

    @Test
    fun structuredUrlWithoutRawBuildsProtocolPortPathAndOrderedQuery() {
        val request = collection(
            """
            {"name":"Structured","request":{"method":"GET","url":{"protocol":"https","host":["example","test"],"port":"8443","path":["users",":id"],"query":[{"key":"tag","value":"a b"},{"key":"tag","value":"x+y"}],"variable":[{"key":"id","value":"42"}]}}}
        """
        ).requests.single()
        assertEquals("https://example.test:8443/users/{{id}}", request.url.text)
        assertTrue(request.url.interpolate)
        assertEquals(listOf("a b", "x+y"), request.query.map { it.value.text })
        assertEquals(mapOf("id" to "42"), request.variables)
    }

    @Test
    fun collectionEnvironmentAndLocalVariablesKeepTheirScopes() {
        val text = source(
            """
            {"name":"First","variable":[{"key":"id","value":"first"}],"request":{"method":"GET","url":"{{base}}/users/{{id}}/{{missing}}"}},
            {"name":"Second","request":{"method":"GET","url":{"raw":"{{base}}/users/:id","variable":[{"key":"id","value":"second"}]}}}
        """,
            """"variable":[{"key":"base","value":"https://collection.test"},{"key":"off","value":"ignored","disabled":true}]"""
        )
        val environment =
            """{"name":"Development","values":[{"key":"base","value":"https://environment.test","enabled":true},{"key":"disabled","value":"ignored","enabled":false}]}"""
        val bundle = PostmanImportParser().parse(ImportInput(text, environment))
        assertEquals(mapOf("base" to "https://collection.test"), bundle.variables)
        assertEquals(mapOf("base" to "https://environment.test"), bundle.environments.single().values)
        assertEquals("first", bundle.requests[0].variables["id"])
        assertEquals("second", bundle.requests[1].variables["id"])
        assertTrue(bundle.requests[0].url.interpolate)
        assertTrue(bundle.requests[0].diagnostics.any { "missing" in it.message })
        assertTrue(bundle.requests.all { it.selectable })
    }

    @Test
    fun standaloneEnvironmentKeepsEnabledValues() {
        val bundle =
            PostmanImportParser().parse(ImportInput("""{"name":"Local","_postman_variable_scope":"environment","values":[{"key":"one","value":123},{"key":"two","value":"no","enabled":false}]}"""))
        assertTrue(bundle.requests.isEmpty())
        assertEquals(ImportedEnvironment("Local", mapOf("one" to "123")), bundle.environments.single())
    }

    @Test
    fun rawFormMultipartAndFileBodiesRetainValuesWithoutReadingFiles() {
        val requests = collection(
            """
            {"name":"JSON","request":{"method":"POST","url":"https://example.test","body":{"mode":"raw","raw":"{\"name\":\"{{name}}\"}","options":{"raw":{"language":"json"}}}}},
            {"name":"Form","request":{"method":"POST","url":"https://example.test","body":{"mode":"urlencoded","urlencoded":[{"key":"q","value":"a+b"},{"key":"q","value":"second"},{"key":"off","value":"no","disabled":true}]}}},
            {"name":"Parts","request":{"method":"POST","url":"https://example.test","body":{"mode":"formdata","formdata":[{"key":"text","value":"{{value}}","type":"text"},{"key":"upload","type":"file","src":["one.txt","two.txt"],"contentType":"text/plain"}]}}},
            {"name":"File","request":{"method":"PUT","url":"https://example.test","body":{"mode":"file","file":{"src":"../payload.bin"}}}}
        """
        ).requests
        val raw = assertIs<ImportBody.Text>(requests[0].body)
        assertEquals("application/json", raw.contentType)
        assertTrue(raw.content.interpolate)
        assertEquals(listOf("a+b", "second"), assertIs<ImportBody.Form>(requests[1].body).fields.map { it.value.text })
        val parts = assertIs<ImportBody.Multipart>(requests[2].body).parts
        assertEquals(listOf("{{value}}", "one.txt", "two.txt"), parts.map { it.value.text })
        assertTrue(parts.drop(1).all { it.file && it.contentType == "text/plain" })
        assertEquals("../payload.bin", assertIs<ImportBody.File>(requests[3].body).path.text)
    }

    @Test
    fun apiKeyAuthenticationCanTargetQueryAndNoRedirectIsRetained() {
        val request = collection(
            """
            {"name":"API","protocolProfileBehavior":{"followRedirects":false},"request":{"method":"GET","url":"https://example.test","auth":{"type":"apikey","apikey":[{"key":"key","value":"api_key"},{"key":"value","value":"{{secret}}"},{"key":"in","value":"query"}]}}}
        """
        ).requests.single()
        assertEquals(ImportAuth.ApiKey("api_key", ImportValue("{{secret}}", true), true), request.auth)
        assertTrue(request.noRedirect)
    }

    @Test
    fun inheritedScriptsUnsupportedAuthAndDynamicVariablesRequireExplicitConsent() {
        val bundle = collection(
            """
            {"name":"Unsafe","request":{"method":"GET","url":"https://example.test/{{${'$'}randomUUID}}","auth":{"type":"oauth2","oauth2":[{"key":"accessToken","value":"not-executed"}]}}}
        """,
            """"event":[{"listen":"prerequest","script":{"type":"text/javascript","exec":["pm.environment.set('token', 'changed');"]}}]"""
        )
        val request = bundle.requests.single()
        assertTrue(request.selectable)
        assertFalse(request.selectedByDefault)
        assertTrue(request.diagnostics.count { it.requiresConsent } >= 3)
        assertTrue(request.diagnostics.any { it.retainedSource?.contains("pm.environment.set") == true })
        assertTrue(request.diagnostics.any { it.retainedSource?.contains("oauth2") == true })
        assertTrue(request.url.text.contains("${'$'}randomUUID"))
    }

    @Test
    fun unsupportedSchemaAndMalformedJsonProduceBlockingDiagnostics() {
        listOf("{broken", source("", schema = "https://schema.getpostman.com/json/collection/v1.0.0/collection.json"))
            .forEach { text ->
                val bundle = PostmanImportParser().parse(ImportInput(text))
                assertTrue(bundle.diagnostics.any { it.severity == ImportSeverity.ERROR })
                assertTrue(bundle.requests.isEmpty())
            }
    }

    @Test
    fun unknownBodyProtocolAndMalformedRequestBlockOnlyAffectedItems() {
        val bundle = collection(
            """
            {"name":"Bad body","request":{"method":"POST","url":"https://example.test","body":{"mode":"graphql","graphql":{"query":"{ user }"}}}},
            {"name":"Bad protocol","request":{"method":"GET","url":"ftp://example.test/file"}},
            {"name":"Missing method","request":{"url":"https://example.test"}},
            {"name":"Valid","request":{"method":"GET","url":"https://example.test"}}
        """
        )
        assertEquals(4, bundle.requests.size)
        assertTrue(bundle.requests.take(3).all { !it.selectable })
        assertTrue(bundle.requests.last().selectedByDefault)
    }

    @Test
    fun version20CollectionAndStringRequestAreAccepted() {
        val bundle = collection(
            """{"name":"Legacy","request":"https://example.test/legacy?x=a%2Bb&x=two"}""",
            schema = "https://schema.getpostman.com/json/collection/v2.0.0/collection.json"
        )
        assertEquals("GET", bundle.requests.single().method)
        assertEquals("https://example.test/legacy?x=a%2Bb&x=two", bundle.requests.single().url.text)
        assertTrue(bundle.requests.single().query.isEmpty())
    }

    @Test
    fun version20AuthenticationObjectUsesItsStaticAttributes() {
        val bundle = collection(
            """
            {"name":"Basic","request":{"method":"GET","url":"https://example.test","auth":{"type":"basic","basic":{"username":"alice","password":"{{password}}"}}}},
            {"name":"API","request":{"method":"GET","url":"https://example.test","auth":{"type":"apikey","apikey":{"key":"X-Api-Key","value":"value","in":"header"}}}}
        """, schema = "https://schema.getpostman.com/json/collection/v2.0.0/collection.json"
        )
        assertEquals(ImportAuth.Basic(ImportValue("alice"), ImportValue("{{password}}", true)), bundle.requests[0].auth)
        assertEquals(ImportAuth.ApiKey("X-Api-Key", ImportValue("value"), false), bundle.requests[1].auth)
        assertTrue(bundle.requests.all { it.selectedByDefault })
    }

    @Test
    fun malformedStringFieldsAreRejectedInsteadOfCoercedToDifferentRequests() {
        val bundle = collection(
            """
            {"name":"Header","request":{"method":"GET","url":"https://example.test","header":[{"key":"X-Test","value":123}]}},
            {"name":"Body","request":{"method":"POST","url":"https://example.test","body":{"mode":"raw","raw":123}}},
            {"name":"Query","request":{"method":"GET","url":{"raw":"https://example.test","query":[{"key":"x","value":123}]}}}
        """
        )
        assertEquals(3, bundle.requests.size)
        assertTrue(bundle.requests.all { !it.selectable })
    }

    @Test
    fun unsupportedBodyOptionsAreRetainedAndRequireConsent() {
        val request = collection(
            """
            {"name":"Options","request":{"method":"POST","url":"https://example.test","body":{"mode":"raw","raw":"text","options":{"raw":{"language":"text","unknownBehavior":true}}}}}
        """
        ).requests.single()
        assertTrue(request.selectable)
        assertFalse(request.selectedByDefault)
        assertTrue(request.diagnostics.any { it.requiresConsent && it.retainedSource?.contains("unknownBehavior") == true })
    }

    @Test
    fun cyclicVariableReferencesRemainBoundedAndNestedDynamicValuesRequireConsent() {
        val bundle = collection(
            """{"name":"Indirect","request":{"method":"GET","url":"{{base}}/{{token}}"}}""",
            """"variable":[{"key":"base","value":"{{base}}"},{"key":"token","value":"{{${'$'}randomUUID}}"}]"""
        )
        assertTrue(bundle.requests.single().selectable)
        assertFalse(bundle.requests.single().selectedByDefault)
        assertTrue(bundle.requests.single().diagnostics.any { "Dynamic" in it.message })
        assertTrue(bundle.requests.single().variables.isEmpty())
    }

    @Test
    fun excessiveJsonNestingProducesAnErrorWithoutRecursingIntoIt() {
        val bundle = PostmanImportParser().parse(ImportInput("[".repeat(101) + "0" + "]".repeat(101)))
        assertTrue(bundle.diagnostics.any { it.severity == ImportSeverity.ERROR && "nesting" in it.message })
    }

    @Test
    fun standaloneEnvironmentReportsUnresolvedDynamicAndCyclicValues() {
        val bundle = PostmanImportParser().parse(
            ImportInput(
                """
            {"name":"Environment","values":[{"key":"missing","value":"{{undefined}}"},{"key":"token","value":"{{${'$'}randomUUID}}"},{"key":"cycle","value":"{{cycle}}"}]}
        """
            )
        )
        assertTrue(bundle.diagnostics.any { "undefined" in it.message && "executable" in it.message })
        assertTrue(bundle.diagnostics.any { it.requiresConsent && it.retainedSource?.contains("${'$'}randomUUID") == true })
        assertTrue(bundle.diagnostics.any { "cycl" in it.message.lowercase() })
        assertEquals(3, bundle.environments.single().values.size)
    }

    @Test
    fun environmentCanReferenceACollectionDefaultWithoutFalseMissingDiagnostics() {
        val bundle = PostmanImportParser().parse(
            ImportInput(
                source("", """"variable":[{"key":"host","value":"example.test"}]"""),
                """{"name":"Dev","values":[{"key":"base","value":"https://{{host}}"}]}"""
            )
        )
        assertTrue(bundle.diagnostics.none { "host" in it.message && "undefined" in it.message })
        assertEquals("https://{{host}}", bundle.environments.single().values["base"])
    }

    @Test
    fun variableFieldNamesBlockOnlyTheirRequestWithPreciseLocations() {
        val bundle = collection(
            """
            {"name":"Header name","request":{"method":"GET","url":"https://example.test","header":[{"key":"X-{{field}}","value":"value"}]}},
            {"name":"Query name","request":{"method":"GET","url":{"protocol":"https","host":"example.test","query":[{"key":"{{field}}","value":"value"}]}}},
            {"name":"Raw query name","request":{"method":"GET","url":{"raw":"https://example.test?{{field}}=value","query":[{"key":"{{field}}","value":"value"}]}}},
            {"name":"Form name","request":{"method":"POST","url":"https://example.test","body":{"mode":"urlencoded","urlencoded":[{"key":"{{field}}","value":"value"}]}}},
            {"name":"Multipart name","request":{"method":"POST","url":"https://example.test","body":{"mode":"formdata","formdata":[{"key":"{{field}}","type":"text","value":"value"}]}}},
            {"name":"API key name","request":{"method":"GET","url":"https://example.test","auth":{"type":"apikey","apikey":[{"key":"key","value":"{{field}}"},{"key":"value","value":"secret"}]}}},
            {"name":"Valid sibling","request":{"method":"GET","url":"https://example.test"}}
        """, """"variable":[{"key":"field","value":"resolved-name"}]"""
        )
        assertEquals(7, bundle.requests.size)
        val locations = listOf(
            "header[0].key", "url.query[0].key", "url.query[0].key",
            "body.urlencoded[0].key", "body.formdata[0].key", "auth.apikey.key"
        )
        bundle.requests.take(6).zip(locations).forEach { (request, location) ->
            assertFalse(request.selectable, request.name)
            assertFalse(request.selectedByDefault, request.name)
            assertTrue(request.diagnostics.any {
                it.severity == ImportSeverity.ERROR && it.location.endsWith(location) &&
                    "name" in it.message && "unsupported" in it.message.lowercase()
            }, request.name)
        }
        assertTrue(bundle.requests.last().selectedByDefault)
        assertTrue(bundle.diagnostics.none { it.severity == ImportSeverity.ERROR })
    }

    @Test
    fun getAndHeadBodiesAreInvalidRowsWhileValidSiblingsStaySelectable() {
        val bundle = collection(
            """
            {"name":"GET body","request":{"method":"GET","url":"https://example.test","body":{"mode":"raw","raw":"text"}}},
            {"name":"HEAD body","request":{"method":"HEAD","url":"https://example.test","body":{"mode":"urlencoded","urlencoded":[]}}},
            {"name":"Disabled GET body","request":{"method":"GET","url":"https://example.test","body":{"mode":"raw","raw":"ignored","disabled":true}}},
            {"name":"POST sibling","request":{"method":"POST","url":"https://example.test","body":{"mode":"raw","raw":"text"}}}
        """
        )
        assertEquals(4, bundle.requests.size)
        bundle.requests.take(2).forEach { request ->
            assertFalse(request.selectable, request.name)
            assertTrue(request.diagnostics.any {
                it.severity == ImportSeverity.ERROR && it.location.endsWith("request.body") && request.method in it.message
            }, request.name)
        }
        assertTrue(bundle.requests.drop(2).all { it.selectedByDefault })
        assertTrue(bundle.diagnostics.none { it.severity == ImportSeverity.ERROR })
    }

    private fun collection(items: String, extra: String = "", schema: String = SCHEMA): ImportBundle =
        PostmanImportParser().parse(ImportInput(source(items, extra, schema)))

    private fun source(items: String, extra: String = "", schema: String = SCHEMA): String =
        """{"info":{"name":"Demo","schema":"$schema"},"item":[$items]${if (extra.isBlank()) "" else ",$extra"}}"""

    companion object {
        private const val SCHEMA = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
    }
}
