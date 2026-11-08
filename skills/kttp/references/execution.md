# Run and debug Kttp

Contents: [runtime](#runtime), [CLI](#cli), [IDEA](#idea), [GUI modes](#gui-modes), [errors](#errors).

## Runtime

The skill is usable without the Kttp source checkout. Execution still needs either the installed IDEA plugin or a
standalone Kttp CLI fat JAR, plus a compatible JDK. Use the user's existing runtime and version. The current plugin
targets IDEA 2025.3.2; JDK 21 is the recommended development and script runtime.

- Installed plugin: its distribution includes `cli/kttp-cli.jar`. End users do not need to build it. Locate the actual
  installed plugin directory rather than inventing a fixed home/config path.
- Source checkout: `./gradlew :core:kttp-cli:shadowJar` produces the standalone fat JAR under
  `core/kttp-cli/build/libs/`. A `-plain.jar` lacks its runtime dependencies. Build prerequisites belong to that
  checkout; do not assume every user's project contains Gradle or Kttp sources.
- No runtime available: provide the script and input requirements, and state that compilation/execution has not been
  verified. Installing a skill does not install the plugin or CLI.

## CLI

Set `KTTP_CLI_JAR` to an actual fat JAR and `KTTP_WORK_DIR` to the directory containing the script and flat environment
JSON. These are shell variables used in the examples, not special Kttp configuration keys.

Compile without evaluating the script body or running its registered HTTP requests:

```bash
java -jar "$KTTP_CLI_JAR" \
  --script "$KTTP_WORK_DIR/requests.kttp.kts" \
  --execution-mode COMPILE_ONLY \
  --no-compilation-cache --json-output
```

Compile checks do not test env values, file existence, request selection, or response assertions. Script dependency
annotations such as `@file:DependsOn` can still resolve/download dependencies during compilation. The examples in this
skill use bundled dependencies only.

Execute one named registration, plus any dependencies it needs:

```bash
java -jar "$KTTP_CLI_JAR" \
  --script "$KTTP_WORK_DIR/requests.kttp.kts" \
  --request-name read-user \
  --env-file "$KTTP_WORK_DIR/env.json" \
  --base-dir "$KTTP_WORK_DIR" \
  --response-dir "$KTTP_WORK_DIR/responses" \
  --json-output
```

This command uses in-memory storage and an explicit response directory. Use the login recipe
in [recipes.md](recipes.md#login-and-dependent-request) and name the script `requests.kttp.kts`, or adapt the command's
filename. Omitting `--storage-dir` prevents a prior stored token from replacing a fresh login.

| Option                          | Actual behavior                                                                                                                        |
|---------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| `--script <file>`               | Required script file input                                                                                                             |
| No request selector             | Execute registered requests in order after script evaluation                                                                           |
| `--request-name <name>`         | Select an explicit request name or delegated property coordinate; use unambiguous names                                                |
| `--request-number <n>`          | Zero-based registration index, not the editor's displayed ordinal                                                                      |
| `--env-file <json>`             | Flat `Map<String, String>`; not IDEA named profiles                                                                                    |
| `--env-param key=value`         | Repeat for individual variables; overrides the env file                                                                                |
| `--base-dir <directory>`        | Relative `localFile` base; defaults to inferred script directory                                                                       |
| `--response-dir <directory>`    | Saved response files; defaults to a subdirectory of system temp                                                                        |
| `--storage-dir <directory>`     | Persistent values; absent means per-process in-memory storage                                                                          |
| `--cookies-file <file>`         | Persistent cookie jar; absent means no cookie session store                                                                            |
| `--execution-mode COMPILE_ONLY` | Compile without evaluating the script body                                                                                             |
| `--execution-mode CURL`         | Produce cURL using the script's constructed request without HTTP transport; still evaluates Kotlin and may execute script side effects |
| `--no-compilation-cache`        | Force a new compilation; useful when validating examples                                                                               |
| `--json-output`                 | Final structured execution result; scripts/compile mode can also print other lines                                                     |

Prefer one selector. The internal IDE `--request-lines first:last` selector takes precedence, then a valid numeric
selector, then a name. Helpers can create several registrations at one source location; unique names are clearer for
those scripts.

Read the process exit code and final result's `success`/`error`. Compilation failures and assertion failures are
execution failures. A returned HTTP 4xx/5xx does not alone imply CLI failure; assert the expected status. JSON output is
not a guarantee that stdout contains exactly one JSON document: use the final result line for this CLI version and
preserve preceding script output when diagnosing failures.

## IDEA

- Create a `.kttp.kts` script through **File → New → Kttp Script**, or in the project. Configure a project JDK for
  execution.
- Gutter Run executes the selected request in script context; **Run All Requests** executes the file's registered
  requests.
- **Execute Kttp Request** defaults to the whole file unless the invoking context supplies request coordinates. Do not
  promise this action always selects the request at the caret.
- Script text output is in the **Run** tool window. Saved responses appear in Kttp's **Responses** tree and **History**.
  The **Response** tab shows the latest GUI Send result.
- Named IDE environments come from `kttp.env.json` in the script directory, then the IDE's Consoles/Kttp directory.
  Select a profile; the IDE forwards its flattened values to the CLI. See [syntax.md](syntax.md#environment) for both
  schemas.

## GUI modes

| Mode          | Semantics                                                                                                                        |
|---------------|----------------------------------------------------------------------------------------------------------------------------------|
| Follow Script | Uses the shared CLI/JDK/argument path with script Run, evaluates Kotlin, executes the selected registration and script callbacks |
| Quick Request | Sends a GUI-representable HTTP model directly, skipping Kotlin evaluation, script client configuration, and `then`               |

Dynamic URLs, login dependencies, assertions, or custom Kotlin client settings normally need Follow Script.
Request labels **Quick available** and **Script required** describe projection support, not the selected execution mode;
hover for editing limits and omitted script processing. **Script required** requests run from the GUI in Follow mode.
Script file rows show the total request count. The **Quick support** filter selects script files by projection capabilities
without changing execution modes. Quick is unavailable when the request cannot be represented by the GUI model.
Follow also needs valid source and successful compilation/evaluation.

New drafts default to Quick; imported script requests default to Follow, with remembered per-request preferences.
Editing a draft changes a working copy; **Save Draft** persists it. **Generate/Update Script** writes generated source,
and Follow Send prepares it before execution. Imported source is saved and executed without replacing its Kotlin with
generated GUI code. **Detach** creates an editable supported projection and switches it to Quick.

Follow has JVM startup and compilation costs, including seconds on a cold run; Quick avoids those phases. Do not infer
total latency from HTTP timing alone or promise the two modes have equal latency.

Send shows an activity indicator and elapsed time. A bounded response preview can appear while the full file is saved
and the script continues. A running preview does not establish final script success, completed assertions or a saved
response file. Use the final execution result; file actions become available when a saved path is reported.

History displays shortened paths but retains absolute file locations; double-click opens the response file. It currently
has no Re-run action. GUI Quick saves project responses under `.kttp/responses`; script Run/Follow use
`<IDE config>/consoles/Kttp/responses`. Preview reads at most 64 KiB of decoded response bytes; open the saved file for the
full body.

## Errors

| Symptom                                                                             | Check/fix                                                                                                                       |
|-------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `Unresolved reference: env` after `env()`                                           | Use `val key: String by env` in a Kttp script runtime                                                                           |
| Unresolved `OkHttpExecutor`, `json {}`, `assert { status(...) }`, or builder `name` | Use registered request functions, `body(serializedJson)`, `.then { assertStatus(...) }`, and `name = ...` as a request argument |
| Status/body properties unavailable in `.then`                                       | Receiver is `ThenContext`; use `statusCode`, cached `body`, `decode`, or `getResponse()`                                        |
| `No response available` after chained `.then`                                       | Combine operations in one deferred callback; the mapped-holder overload expects a response already received                     |
| Request-result delegate has no applicable `getValue`                                | Include the login recipe's top-level compatibility getter; the current runtime delegate getter lacks `operator`                 |
| Cannot access holder `execute()`                                                    | It is internal; use the tested delegated login result rather than invoking it directly                                          |
| `var ... by variable()` fails to compile                                            | Use explicit `vars.setValue` / `vars.getValue` as in the stored-values recipe                                                   |
| Outer `vars`/builder member cannot be called with an implicit receiver              | Capture it at script level, for example `val savedValues = vars`, then use that local value inside the nested DSL block         |
| Missing environment                                                                 | Check selected IDE profile or flat CLI keys; error text mentioning `--env` does not establish a supported flag                  |
| Wrong local file                                                                    | Check `--base-dir`, script vs project location, and whether `File` or `localFile` is used                                       |
| `kttp-cli.jar not found`                                                            | Installed users need the full plugin ZIP; source developers can build `shadowJar` and restart the intended sandbox              |
| Requests differ between GUI and script                                              | Check Execution mode, saved source, environment, callback/client settings, and Quick projection limits                          |
| Login unexpectedly skipped                                                          | Check stored delegated-property values and `--storage-dir`; use a fresh in-memory invocation                                    |
| HTTP 2xx but command fails                                                          | Inspect `then` assertion/extraction errors; HTTP arrival and script success are separate                                        |
