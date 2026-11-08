# Kttp script syntax

Contents: [requests](#requests), [body and auth](#body-and-auth), [responses](#responses), [environment](#environment), [files](#files), [client configuration](#client-configuration).

## Requests

Use `.kttp.kts` files with the Kttp scripting runtime. Ordinary `.kt` files do not receive the script's builder instance
or default imports.

`GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS`, and `TRACE` share the signature
`METHOD(path: String, name: String? = null, configure: RequestBuilder.() -> Unit = {})`. Despite its parameter name,
`path` is the request URL. Custom methods use `request(method, path, name) { ... }`. The result is a registered
`RequestHolder`, not a received HTTP response.

| RequestBuilder operation  | Accepted form                                                                            |
|---------------------------|------------------------------------------------------------------------------------------|
| One header                | `header("X-Trace", "sample")`                                                            |
| Several headers           | `headers("Accept" to "application/json", "X-Trace" to "sample")` or a `Map<String, Any>` |
| One/repeated query values | `query("tag", "a")`; another call appends another value                                  |
| Several query values      | `query("page" to 1, "size" to 20)` or `queries(mapOf("page" to 1))`                      |
| Path placeholders         | URL `/users/{id}`, then `path("id", 42)`; map and vararg forms also exist                |
| Media type                | `contentType("application/json")`, `accept("application/json")`                          |

Query values are encoded by OkHttp. Path placeholder replacement inserts `toString()` values into the URL before
parsing; it is not a general arbitrary-path-segment escaping API. Use suitable identifiers or explicitly encode values
when required by the API.

Complete script:

```kotlin
val baseUrl: String by env

GET("$baseUrl/users/{id}", name = "read-user") {
    path("id", 42)
    query("tag", "a b")
    query("tag", "kotlin")
    headers("Accept" to "application/json", "X-Trace" to "example")
}.then {
    assertStatus(200)
    assertEquals(42, decode<Int>("$.id"))
}
```

## Body and auth

Set one body per request; combining `body`, `formData`, or `multipart` on the same builder throws `Body already set`.

| Body             | API                                                                              |
|------------------|----------------------------------------------------------------------------------|
| Text/JSON        | `body(text: String)` plus an appropriate `contentType(...)`                      |
| Binary           | `body(bytes: ByteArray)`                                                         |
| URL-encoded form | `formData { field("name", value) }`; supplies form content type                  |
| Multipart        | `multipart { part("label") { body("text") }; file("upload", "name.txt", file) }` |

There is no `json { ... }` body builder. Serialize dynamic JSON first; see [recipes](recipes.md#json-body).

Authentication helpers are `basicAuth(username, password)` and `bearerAuth(token)` inside the request builder. API keys
use the actual API's header or query parameter, for example `header("X-API-Key", apiKey)` or `query("api_key", apiKey)`.
OAuth support exists as an experimental API; do not assume a provider-specific flow without checking its configuration.

## Responses

Kttp supplies default imports for request functions, builder extensions, `then`, `thenResponse`, `localFile`,
`kotlin.test.*`, and AssertJ `assertThat`. JVM types such as `File` and `TimeUnit`, and the JSON mapper shown in the
recipes, need explicit imports.

| `.then` member                                                        | Meaning                                                     |
|-----------------------------------------------------------------------|-------------------------------------------------------------|
| `assertStatus(200)`                                                   | Throw `AssertionError` on a different status                |
| `assertHeader("X-Result", "ok")`                                      | Case-insensitive header-name lookup; exact value comparison |
| `assertBodyContains("text")`                                          | Check response text contains a substring                    |
| `statusCode` / `headers` / `body`                                     | Status integer, header map, cached text                     |
| `decode<T>("$.field")`                                                | Typed JSONPath extraction; default path is `$`              |
| `jsonPath().readString/readInt/readLong/readDouble/readBoolean(path)` | Typed convenience readers                                   |
| `getResponse()`                                                       | Underlying OkHttp `Response`                                |

Within one `ThenContext`, `body` is cached and supports repeated assertions/decodes. Its first access consumes the
underlying response body's stream. Avoid mixing it with separate `getResponse().body?.string()` reads or multiple
body-consuming callbacks on one response. Combine related checks/extraction in one `.then` block. Appending another
`.then` to a mapped holder invokes a different overload that expects an already received response; it does not build
another deferred callback.

For a raw response receiver:

```kotlin
val baseUrl: String by env

GET("$baseUrl/health", name = "raw-response").thenResponse {
    requireStatus(200)
    assertEquals(200, code)
    val text = body?.string().orEmpty()
    assertTrue(text.contains("ok"))
}
```

`requireStatus` throws `IllegalArgumentException` on mismatch. In `.then`, call `getResponse().requireStatus(200)` or
use `assertStatus(200)`.

## Environment

Declare typed properties with `by env`: `val baseUrl: String by env`. The property name is the key. Supported scalar
types are `String`, `Int`, `Long`, `Boolean`, and `Double`. Missing selected-environment values fail when needed; do not
invent fallback values or assume OS variables are imported automatically.

IDEA `kttp.env.json` contains named environments:

```json
{
  "dev": {
    "baseUrl": "http://127.0.0.1:8080",
    "username": "demo",
    "password": "replace-me"
  }
}
```

Select `dev` in IDEA. Lookup checks the script directory, then `<IDE config>/consoles/Kttp/`; it does not search all
ancestor directories.

CLI `--env-file` instead accepts a flat string map:

```json
{
  "baseUrl": "http://127.0.0.1:8080",
  "username": "demo",
  "password": "replace-me"
}
```

`--env-param key=value` can be repeated and overrides the flat file's values. There is no CLI `--env` selector for IDEA
profile names.

GUI fields use a separate value codec: `${env:baseUrl}` must occupy the whole field. `${env:baseUrl}/users` is not
interpolation there. Kotlin scripts use normal `"$baseUrl/users"` interpolation. JSON/Raw GUI bodies also support
`@file:path/to/body.json`.

## Files

`localFile(path)` delegates text loading; UTF-8 and `required = true` are defaults. A missing/unreadable optional file
(`required = false`) yields empty text. It is not a binary upload helper.

```kotlin
val baseUrl: String by env
val payload: String by localFile("request.json")

POST("$baseUrl/documents", name = "file-body") {
    contentType("application/json")
    body(payload)
}.then {
    assertStatus(200)
}
```

- An absolute filesystem path is read directly. `file://` removes that prefix and treats the remainder as a filesystem
  path; it is not a general URI parser.
- Relative `localFile` paths use the runner's base directory. IDEA supplies the project root when available; CLI
  `--base-dir` overrides the otherwise inferred script directory. Without a directory context, resolution falls back to
  the working directory.
- A nonempty `classpath:/x.json` with a base directory reads `<base>/src/main/resources/x.json`; without a base it reads
  the JVM classpath.
- Paths containing `src/` use a separate ancestor search for a directory containing `src`, then working-directory
  fallback.
- The delegate captures directory context when constructed. Define it during script evaluation, outside deferred
  request/callback blocks. A delegate constructed later may see the cleared script context.
- `java.io.File("relative")` uses the JVM working directory, not `localFile` resolution. For binary upload use a
  supplied absolute file path or deliberately construct a known base path.

## Client configuration

`configureClient { ... }` configures the script's OkHttp client builder. Request-local `configure { ... }` tweaks a
request's client; `configureRequest { ... }` tweaks its OkHttp request builder.

```kotlin
import java.util.concurrent.TimeUnit

val baseUrl: String by env

configureClient {
    connectTimeout(5, TimeUnit.SECONDS)
    callTimeout(30, TimeUnit.SECONDS)
}

GET("$baseUrl/health", name = "configured") {
    noRedirect()
    noCookies()
    configure { readTimeout(10, TimeUnit.SECONDS) }
}.then {
    assertStatus(200)
}
```

`http2()` selects OkHttp `H2_PRIOR_KNOWLEDGE`, not automatic HTTP/2 negotiation. Enable it only for a matching endpoint.
GUI Quick Request skips script `configureClient` and request-local Kotlin configuration; use Follow Script to preserve
those settings.
