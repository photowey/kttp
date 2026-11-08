---
name: kttp
description: Use when writing, editing, explaining, or debugging Kttp .kttp.kts HTTP scripts, translating cURL or HTTP requests into Kttp, or choosing between Kttp script execution and GUI request modes.
---

# Kttp

Create executable Kotlin HTTP scripts for Kttp's IntelliJ IDEA plugin or standalone CLI. Answer in the user's language.
This skill targets the `.kttp.kts` API; use an existing project's runtime and conventions when they differ.

## Start with the request

Keep the user's endpoint, authentication, payload, and intended assertions. Reuse their existing scripts and environment
configuration. Give requests unique `name` arguments when they need independent execution.

For an ordinary request, start here:

```kotlin
val baseUrl: String by env

GET("$baseUrl/health", name = "health") {
    accept("application/json")
}.then {
    assertStatus(200)
    assertEquals("ok", decode<String>("$.status"))
}
```

Supply `baseUrl` through the selected IDE environment or CLI input. It is a user-configured value, not an automatic
OS-environment lookup.

## Read only the relevant reference

| Need                                                                        | Reference                               |
|-----------------------------------------------------------------------------|-----------------------------------------|
| Exact request builders, auth, callbacks, env/files, client configuration    | [syntax.md](references/syntax.md)       |
| JSON, forms, uploads, login dependencies, Kotlin reuse, stored values       | [recipes.md](references/recipes.md)     |
| IDEA/CLI setup, single-request selection, compile checks, GUI modes, errors | [execution.md](references/execution.md) |

Read the login dependency recipe before building a request chain. Read the execution reference before supplying CLI
flags or diagnosing a difference between GUI and script behavior. These references are included in the installed skill;
a source checkout is not required.

## Essential semantics

- Use `val baseUrl: String by env`, with no parentheses. The property name is the environment key. Use Kotlin
  interpolation, such as `"$baseUrl/users"`, in scripts.
- Use `GET(url, name = "read-user") { ... }`; `name` is an argument of the request function, not a builder property.
- Request calls register holders. Their builder blocks and HTTP calls normally run in the execution phase. Reading a
  delegated request result can execute a prerequisite. Use the login recipe, including its current-runtime delegate
  compatibility getter; direct holder execution is internal.
- `.then { ... }` receives `ThenContext`. Use `assertStatus`, `decode<T>`, `body`, or `getResponse()`. It is not an
  infix call and has no `assert { status(...) }` DSL.
- `.thenResponse { ... }` receives OkHttp `Response`; use `code` or `requireStatus`. Do not assume the same callback
  members exist in both contexts.
- A request name selects a registration after script evaluation. Top-level Kotlin still runs; dependencies may also run.
  Selecting one request is not a sandbox around one source block.
- Use a JSON serializer for dynamic values. `body(String)` sends the supplied text; it does not turn a Kotlin map into
  JSON or escape interpolated values.
- Follow Script runs the script and its callbacks. Quick Request sends a GUI projection and skips script evaluation,
  global script configuration, and `then`. Use Follow Script when the script's behavior matters.

## Produce and verify

Return the script, required environment/file inputs, and the relevant execution command or IDEA action. Distinguish a
complete script from an illustrative fragment. When adapting cURL, preserve query encoding, repeated headers, content
type, form vs JSON semantics, and authentication rather than mechanically copying shell quoting.

When a CLI is available, first compile the script using `COMPILE_ONLY`; then run the requested scenario if execution is
in scope. Compilation alone does not validate credentials, file existence, callback assertions, or server behavior. For
test fixtures use a local mock endpoint. Report what actually ran and any remaining input/runtime gaps.

The skill supplies instructions; it does not install the Kttp plugin, CLI, JDK, or Node.js.
Use [execution.md](references/execution.md) for runtime discovery and setup.
