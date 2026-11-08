# Kotlin scripting

Kttp scripts use the `.kttp.kts` extension and the Kttp scripting runtime. An ordinary `.kt` file does not receive
the script's builder instance or default imports. Request declarations are registered for later execution; they do not
immediately return an HTTP response.

## Build a request

`GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS` and `TRACE` accept a URL, an optional `name`, and a builder
block. Use `request(method, path, name)` for a custom method. Here is a complete script:

```kotlin
val baseUrl: String by env

GET("$baseUrl/users/{id}", name = "read-user") {
    path("id", 42)
    query("tag", "kotlin")
    query("tag", "http client")
    header("Accept", "application/json")
}.then {
    assertStatus(200)
    assertEquals(42, decode<Int>("$.id"))
}
```

Repeated `query` calls append values, encoded by OkHttp. `path` substitutes values before URL parsing; use suitable
identifiers or encode arbitrary path segments explicitly. Keep request names unique when selecting them from the CLI.

## Bodies and authentication

Use one body per request: `body`, `formData` and `multipart` cannot be combined on the same builder.

```kotlin
val baseUrl: String by env
val apiToken: String by env

POST("$baseUrl/documents", name = "create-document") {
    bearerAuth(apiToken)
    contentType("application/json")
    body("""{"title":"Kttp example","enabled":true}""")
}.then {
    assertStatus(201)
}
```

Use `basicAuth(username, password)` for Basic authentication, and normal headers/query values for API keys.
`formData { field("name", value) }` performs form encoding. `multipart` accepts text parts and file parts; current
multipart construction buffers uploads in memory. There is no `json { ... }` body builder. Serialize dynamic values
with a JSON library rather than interpolating unescaped text into JSON.

## Check and read a response

The `then` receiver is **ThenContext**. It offers `statusCode`, `headers`, cached `body`, `assertStatus`,
`assertHeader`, `assertBodyContains`, and typed JSONPath `decode<T>(path)`.

Keep related checks and extraction in one `then` block. The first body read consumes the stream and caches the text;
avoid also consuming `getResponse().body` directly. Calling `then` again on an already mapped holder uses an immediate
overload that requires a received response; it does not add another deferred callback.

For an OkHttp `Response` receiver, use `thenResponse { ... }`, or obtain it through `getResponse()` inside `then`.

## Environment profiles

Declare `val baseUrl: String by env`; the property name is the key. Scalar types include `String`, `Int`, `Long`,
`Boolean` and `Double`. This is a property delegate, not `env()`, and does not automatically read OS environment values.

IDEA reads **named profiles** from `kttp.env.json` in the script's directory. Only if that file is absent does it use
`<IDE config>/consoles/Kttp/`. An invalid local file or a missing profile does not trigger a second lookup or merge
profiles from both locations. Select a profile in the editor:

```json
{
  "dev": {
    "baseUrl": "http://127.0.0.1:8080",
    "apiToken": "replace-with-your-test-token"
  }
}
```

CLI `--env-file` expects a **flat map of strings**, rather than that profile wrapper:

```json
{
  "baseUrl": "http://127.0.0.1:8080",
  "apiToken": "replace-with-your-test-token"
}
```

CLI `--env-param key=value` overrides file values. There is no CLI `--env` profile selector.

## Local files and client settings

Use `val payload: String by localFile("request.json")` and `body(payload)` to load a UTF-8 text body. Relative paths
use the runner's base directory: the IDEA project root when available, CLI `--base-dir` when provided, otherwise the
inferred script directory. An absolute path is read directly. `localFile` is not a binary upload helper.

Top-level `configureClient { ... }` configures OkHttp; request-local `configure { ... }` changes that request's client.
Use `noRedirect()` and `noCookies()` for those transport choices. `http2()` selects `H2_PRIOR_KNOWLEDGE`, not automatic
HTTP/2 negotiation. [Follow Script](gui.md) preserves script configuration; Quick Request skips it.
