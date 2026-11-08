# Kttp recipes

Each Kotlin block is a complete, independent `.kttp.kts` script. Supply its environment keys and adapt endpoint
paths/statuses to the real API. Examples use `baseUrl` so they can run against local fixtures.

Contents: [JSON](#json-body), [form](#url-encoded-form), [upload](#multipart-upload), [login dependency](#login-and-dependent-request), [Kotlin reuse](#reuse-kotlin-functions), [stored values](#stored-values).

## JSON body

Serialize dynamic data with Kttp's bundled Jackson mapper. This handles quotes, backslashes, and control characters in
values. No additional Maven dependency is needed.

```kotlin
import io.github.photowey.kttp.core.context.defaultObjectMapper

val baseUrl: String by env
val username: String by env

POST("$baseUrl/documents", name = "create-document") {
    contentType("application/json")
    body(defaultObjectMapper.writeValueAsString(mapOf("owner" to username, "enabled" to true)))
}.then {
    assertStatus(200)
    assertEquals(username, decode<String>("$.owner"))
}
```

A fixed JSON literal can use Kotlin triple quotes: `body("""{"enabled":true}""")`. Triple quotes do not JSON-escape
interpolated variables.

## URL-encoded form

```kotlin
val baseUrl: String by env
val username: String by env
val password: String by env

POST("$baseUrl/form", name = "submit-form") {
    formData {
        field("username", username)
        field("password", password)
    }
}.then {
    assertStatus(200)
    assertEquals(username, decode<String>("$.username"))
}
```

`field` performs form encoding. Do not pre-encode values or label this body as JSON.

## Multipart upload

Here `uploadPath` is an absolute filesystem path supplied by the user. `File` does not use Kttp's `localFile` base
directory. Multipart uploads are currently buffered in memory; this recipe is not a streaming large-file upload.

```kotlin
import java.io.File

val baseUrl: String by env
val uploadPath: String by env

POST("$baseUrl/upload", name = "upload-file") {
    multipart {
        part("description") { body("Kttp upload") }
        file("upload", "sample.txt", File(uploadPath))
    }
}.then {
    assertStatus(200)
    assertEquals("sample.txt", decode<String>("$.filename"))
}
```

For an explicit part MIME type, use
`part(contentType = "text/plain") { contentDisposition(args = listOf("name" to "upload", "filename" to "sample.txt")); body(bytes) }`.
The convenience `file` helper does not infer a MIME type from the filename.

## Login and dependent request

This API returns `{"token":"..."}` from `/login` and `{"id":42}` from `/users/42`. Give the mapped login result a
delegated property. Its callback's last expression supplies the value. The current runtime's `ValueDelegate.getValue`
lacks Kotlin's `operator` modifier, so include the small extension below for top-level delegated request properties. It
forwards to the existing member getter and retains Kttp's dependency execution.

```kotlin
import io.github.photowey.kttp.core.context.defaultObjectMapper
import io.github.photowey.kttp.dsl.ValueDelegate
import kotlin.reflect.KProperty

operator fun <T> ValueDelegate<T>.getValue(receiver: Nothing?, property: KProperty<*>): T =
    this.getValue(receiver, property)

val baseUrl: String by env
val username: String by env
val password: String by env

val token: String by POST("$baseUrl/login", name = "login") {
    contentType("application/json")
    body(defaultObjectMapper.writeValueAsString(mapOf("username" to username, "password" to password)))
}.then {
    assertStatus(200)
    decode<String>("$.token")
}

GET("$baseUrl/users/{id}", name = "read-user") {
    path("id", 42)
    bearerAuth(token)
}.then {
    assertStatus(200)
    assertEquals(42, decode<Int>("$.id"))
}
```

Run with `--request-name read-user` to select the user registration. When building that request, reading `token`
executes the login dependency if its stored value is absent. A mapped holder remembers its non-null mapped result; when
it executes the original dependency itself, it marks that registration ignored for the later execution phase. Running
the complete file normally executes login first and stores the token for the user request.

Delegated results use storage keyed by property name. Default CLI storage is per invocation, in memory. With persistent
storage, an existing `token` can be reused instead of refreshing login; omit `--storage-dir` for a fresh login test. Use
distinct property names for unrelated workflows sharing storage and non-null mapped values.

`val token = POST(...).then { ... }` assigns a holder, not a token. Keep the `by` and compatibility getter above. Do not
call the holder's `execute()` directly: it is internal to the runtime. Avoid creating a second login to extract the same
token or reading a consumed response body twice. If a future runtime supplies an operator getter, recheck whether the
compatibility extension is still needed.

## Reuse Kotlin functions

Ordinary Kotlin functions, loops, and data transformations are available. Names must remain unique when one helper
creates several registrations; selecting a name still evaluates the helper calls and all other top-level code.

```kotlin
val baseUrl: String by env

fun checkUser(id: Int) {
    GET("$baseUrl/users/{id}", name = "user-$id") {
        path("id", id)
    }.then {
        assertStatus(200)
        assertEquals(id, decode<Int>("$.id"))
    }
}

listOf(41, 42).forEach(::checkUser)
```

## Stored values

For explicit values shared through the runtime storage, use `vars.setValue` and typed `vars.getValue`. Capture `vars`
outside a request/callback: Kotlin's DSL receiver marker hides the outer builder's implicit members in those blocks. A
missing value returns null. Persistence is a runner configuration, not a property of the script alone. The current
`variable()` delegate also lacks an operator getter; use these explicit methods rather than assuming
`var ... by variable()` compiles.

```kotlin
import kotlin.reflect.typeOf

val baseUrl: String by env
val savedValues = vars

GET("$baseUrl/users/42", name = "remember-user").then {
    assertStatus(200)
    savedValues.setValue("lastUserId", decode<Int>("$.id"))
    assertEquals(42, savedValues.getValue<Int>("lastUserId", typeOf<Int>()))
}
```

CLI `--storage-dir <directory>` enables persistent JSON storage. Without it the CLI uses in-memory values. GUI Follow
Script and editor Run do not pass this option through their shared invocation model, so do not promise cross-run
persistence from those entry points.
