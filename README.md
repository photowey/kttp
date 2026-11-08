# `Kttp`

[English](README.md) | [简体中文](README.zh_CN.md) | [Online manual](https://photowey.github.io/kttp/)

`Kttp` is an HTTP client built around Kotlin scripts, with a command-line runner and an IntelliJ IDEA plugin. Write
requests in `.kttp.kts` files or compose them in the Kttp tool window.

## Documentation

The [bilingual manual](docs/design/README.md) covers scripting, GUI execution, the CLI, architecture, and development.
After publishing a stable GitHub Release, the release workflow deploys its documentation to
[Kttp Guide](https://photowey.github.io/kttp/).
See [Development and Releases](docs/design/docs/development.md) for CI, Pages setup and the release checklist.

## Modules

| Module           | Responsibility                                                                                                      |
|------------------|---------------------------------------------------------------------------------------------------------------------|
| `core/kttp-dsl`  | HTTP DSL, OkHttp execution, script definition, response capture, environment variables, storage, and file resources |
| `core/kttp-cli`  | Kotlin script compilation/evaluation and the CLI entry point                                                        |
| `idea/kttp-idea` | IDEA file/editor integration, script Run configurations, GUI composer, responses, and history                       |

## Build and install locally

The plugin targets IntelliJ IDEA **2025.3.2**, with `sinceBuild=253.0`, and requires the Kotlin plugin and the platform
language/JSON modules declared in `plugin.xml`. Other IDE products have not been independently verified.
Use JDK 21 for development. Script execution requires a configured project JDK; JDK 21 is recommended.

The Gradle Wrapper downloads Gradle 8.13 from the official HTTPS distribution and verifies its SHA-256 checksum.
The first build needs network access for Gradle, dependencies, and the IntelliJ Platform, but no developer-specific
Maven repository or home directory settings.

Dependencies resolve through Maven Central and Gradle Plugin Portal, with IntelliJ Platform dependencies using the
platform plugin's default repositories. No repository profile is required. Keep machine-specific Gradle settings in your
own `~/.gradle/gradle.properties`. The development IDE uses the IntelliJ Platform plugin's sandbox under
`idea/kttp-idea/build/idea-sandbox/`; it does not override the JVM's `user.home`.

Import the repository root as a single Gradle project. The `core/` and `idea/` directories group the modules declared in
the root `settings.gradle.kts`; they are not separate builds.

Build the plugin:

```bash
./gradlew :idea:kttp-idea:buildPlugin
```

In IDEA, choose **Settings → Plugins → Install Plugin from Disk…** and select
`idea/kttp-idea/build/distributions/kttp-idea.zip`. The ZIP includes `cli/kttp-cli.jar`; plugin users do not need to
build the CLI separately.

## Write and run a script

Choose **File → New → Kttp Script** to create a script under **Scratches and Consoles → Consoles → Kttp**, or create a
`.kttp.kts` file in your project.

```kotlin
GET("https://httpbin.org/get", name = "Get example") {
    query("source", "kttp")
}.then {
    assertStatus(200)
    val url: String = decode("$.url")
    println(url)
}

POST("https://httpbin.org/post", name = "Post JSON") {
    header("Content-Type", "application/json")
    body("""{"message":"Hello from Kttp"}""")
}.then {
    assertStatus(200)
    assertBodyContains("Hello from Kttp")
}
```

- Click a request's gutter Run icon to execute that request in its script context.
- **Execute Kttp Request** in the editor context menu, or its default `Ctrl+Alt+K` shortcut, runs the file unless its
  calling context supplies request coordinates. Use gutter Run to select one request. Shortcut bindings depend on your
  keymap.
- Use **Run All Requests** to execute the file's registered requests in order.

Script output appears in the IDEA **Run** tool window. Saved response files also appear in Kttp's **Responses** tree and
**History**. The Kttp **Response** tab displays the latest GUI Send result.

The `then` receiver is `ThenContext`, with methods such as `assertStatus`, `decode`, and `getResponse`. For a `Response`
receiver, use `thenResponse`.

## GUI execution modes

Open **View → Tool Windows → Kttp**. Create a **New Draft**, fill in a URL, and choose an execution mode:

| Mode              | Execution                                                                                                                                                                          | Use it when                                                                           |
|-------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| **Follow Script** | Uses the same CLI, JDK selection, and argument construction as script Run. Evaluates the script and executes the selected registration, including script configuration and `then`. | You need the source script's behavior, dynamic Kotlin values, or response processing. |
| **Quick Request** | Sends the displayed HTTP request directly through the GUI executor. Skips Kotlin script evaluation, script-level configuration, and `then`.                                        | You want a direct HTTP request using values the composer can represent.               |

New drafts default to Quick Request; imported script requests default to Follow Script. A saved per-request choice
overrides the default. Quick Request is unavailable when the GUI cannot extract a direct HTTP request from the script.
Use Follow Script in that case; the source must still compile and execute successfully.

Imported requests are read-only in the composer. Edit their source script, or use **Detach** on a supported projection
to create an editable draft. Changing the execution mode does not make an imported request editable.

Follow Script evaluates the source file, including top-level Kotlin code, and may execute delegated request dependencies.
JVM startup and script compilation can take seconds, especially on a cold run; Quick Request skips those steps.
**Total** includes preparation, HTTP execution, body download, file saving and result decoding; Follow Script also
includes JVM startup, compilation when needed and script callbacks. **HTTP** measures only the final exchange until
response headers, excluding compilation and body download.

During Send, a spinner and elapsed time indicate that execution is in progress. **Cancel** requests cancellation.
The GUI can display a bounded response preview while the full response is being saved and the script is still running.
The preview is marked as running until execution finishes, when final assertions, timing and history are updated.
An HTTP 200 preview does not establish script success or a completed file save. Response file actions become
available once the saved file's path is known.

Drag the dividers to resize the navigation, parameter/script panels, and response area. See the
[GUI guide](docs/design/docs/gui.md) for request editing, response previews and file actions.

## Drafts, scripts, and saved responses

- **Save Draft** saves the GUI draft. Editing fields does not immediately rewrite a script.
- **Generate Script / Update Script** writes a script from a GUI-owned draft. If a generated file has been changed
  manually, including unsaved editor changes, a new file is created to preserve those changes. Generating a script also
  saves the draft and its file association.
- **Follow Script → Send** prepares a generated script for a GUI-owned draft. For an imported request, it saves and runs
  the current source document without replacing it with generated code.
- **Detach** copies HTTP data from a supported script projection into an editable draft and switches to Quick Request.
  It leaves the source unchanged and omits its Kotlin logic, client configuration and callbacks. Use Follow Script on
  the original request to retain those behaviors, and **Save Draft** to keep the new draft.
- Script file rows show the total request count. Request rows show **Quick available** or **Script required**;
  hover for editing limits and omitted script processing. These labels describe support, not the selected execution
  mode. **Script required** requests run from the GUI with Follow Script. The **Quick support** filter selects script
  files by projection capabilities without changing the execution mode.
- **History** displays shortened paths and retains absolute paths for file access. Double-click an entry to open its
  response file. History is shared across projects using the same IDE configuration directory and does not currently
  offer Re-run.

| Location                                           | Purpose                                                                                        |
|----------------------------------------------------|------------------------------------------------------------------------------------------------|
| `<project>/.kttp/requests/`                        | Scripts generated from GUI drafts                                                              |
| `<project>/kttp/temp/`                             | Sidebar Add Request scripts when no script is selected; requires a local project directory     |
| `<project>/.kttp/responses/`                       | Quick Request response files in a project                                                      |
| `<project>/.kttp/gui-state/drafts.json`            | Saved GUI drafts                                                                               |
| `<IDE config>/consoles/Kttp/`                      | Console scripts; also generated GUI scripts when no project base directory is available        |
| `<IDE config>/consoles/Kttp/gui-state/drafts.json` | Saved GUI drafts when no project base directory is available                                   |
| `<IDE config>/consoles/Kttp/responses/`            | Script Run / Follow Script responses, fallback Quick responses, and shared `kttp-history.json` |

`<IDE config>` is the configuration directory of the running IDE; it depends on the IDE product, version, and settings.
Response previews read at most 64 KiB of decoded body data. The GUI takes the first 50,000 characters from that preview
before optional JSON formatting; open the saved response file to inspect the full body.

See the [script syntax reference](skills/kttp/references/syntax.md) for environment variables and local files, and
the [execution reference](skills/kttp/references/execution.md) for IDEA/CLI execution and GUI modes. These references
are in English.

## Agent skill

Install the `kttp` skill to help coding agents write, explain, and debug `.kttp.kts` scripts:

```bash
npx skills add photowey/kttp
# Select this skill explicitly:
npx skills add photowey/kttp --skill kttp
```

Installation requires Node.js and npm; check the [Skills CLI](https://github.com/vercel-labs/skills) for its supported
Node.js versions. The CLI lets you choose the target agent and installs to the current project by default; use `-g`
for a user-level installation. The skill includes syntax, complete request recipes, and IDEA/CLI execution guidance.
It does not install the Kttp plugin, CLI, or JDK.

The commands above read the published GitHub repository. To preview unpublished changes from a local checkout:

```bash
npx skills add . --list
npx skills add . --skill kttp
```

See [the skill entry point](skills/kttp/SKILL.md).

## Development commands

```bash
./gradlew build
./gradlew test
./gradlew :idea:kttp-idea:runIde
./gradlew :idea:kttp-idea:buildPlugin
./gradlew :core:kttp-cli:shadowJar
```

`runIde` starts a development IDEA instance with the plugin installed. `shadowJar` produces the standalone CLI fat JAR
in `core/kttp-cli/build/libs/`; the `-plain.jar` artifact does not bundle its dependencies.

## Current limits

- Quick Request sends only the HTTP data represented by the GUI; use Follow Script for Kotlin script behavior.
- Quick Request uses a 60-second call timeout and does not maintain a persistent cookie jar.
- HTTP and Postman import actions are placeholders.
- `http2()` / the HTTP/2 checkbox selects OkHttp's `H2_PRIOR_KNOWLEDGE` mode; it is not an automatic HTTP/2 negotiation
  switch.
- Compatibility with every IDE product or third-party theme has not been independently verified.

## Acknowledgements

We thank the teams behind [Connekt](https://github.com/Amplicode/connekt) and [Amplicode](https://amplicode.ru/) for the
ideas and implementation approaches that have informed Kttp's Kotlin HTTP scripting and IntelliJ IDEA integration.
