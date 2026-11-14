# Architecture

Kttp has three Gradle modules. The standalone CLI runs Kotlin scripts; the IDEA plugin provides editor, project
and GUI integration.

| Module | Responsibilities |
| --- | --- |
| `core/kttp-dsl` | Request builders and registration, runtime context, OkHttp execution, scripting definition, response capture, environment/storage and resources |
| `core/kttp-cli` | CLI arguments, script compilation/evaluation, request selection and structured execution output |
| `idea/kttp-idea` | File type/editor support, script Run, request projection, draft storage, direct GUI execution, responses and history |

OkHttp execution is part of `kttp-dsl`; the current project has no separate `kttp-executor` module. Core modules do
not depend on IntelliJ PSI, editor or Swing APIs. The IDEA host depends on the DSL and bundles the separate CLI fat JAR.

## Script execution

```text
.kttp.kts source
    → Kttp CLI / Kotlin scripting host
    → evaluate Kotlin and register requests
    → select registrations and resolve delegated dependencies
    → RequestBuilder / runtime context / OkHttp
    → response capture, saved response, then / thenResponse
    → CLI result and IDEA presentation
```

Request holders defer request building and execution. Callbacks can assert a response or map it into a value;
delegated values can introduce request dependencies. Source selection limits registered request execution, not the
top-level Kotlin code evaluated while loading the script.

Editor Run and GUI Follow Script use the shared CLI locator, project JDK selection and argument construction.
The plugin ZIP includes `cli/kttp-cli.jar`, so installed users do not need a source checkout or a separate CLI build.

## GUI execution and source ownership

```text
script PSI → static request projection → read-only imported model
                                            │
                          Follow Script ────┴──→ saved source → bundled CLI

new draft / Detach → editable RequestSpec → Quick Request → OkHttp
                              │
                              └→ generate linked script → Follow Script
```

Static projection extracts the HTTP data the GUI can represent without evaluating arbitrary Kotlin. Capability labels
and the file filter describe that support independently of the selected execution mode. Follow Script also runs
requests that cannot be represented as a direct GUI HTTP model.

Draft editing changes a working copy; saving, script generation and execution are separate operations. Imported
requests use their source script. The generator checks file fingerprints to preserve manual edits, including unsaved
editor changes. A completed send updates draft metadata only if the draft's identity and revision still match,
preventing the result from overwriting a different request after the user changes selection.

## Request import

The IDEA module's `importing` package separates static source parsing from preview, conversion and persistence:

```text
cURL / Postman → parser registry → ImportBundle
                                  → request adapter / script renderer
                                  → shared preview and selection
                                  → script / GUI draft / environment writer
```

`RequestImportParser` strategies return immutable requests, variables, environments, source paths and diagnostics
without IntelliJ or Swing dependencies. The explicit registry selects the format. The unified model retains the HTTP
method as a string, so custom methods survive GUI enum limits. Postman folders contribute order, source paths and
inherited settings rather than a separate collection workspace.

The host adapter reuses `RequestSpec`, `GuiDraft` and `GuiScriptGenerator`. Script generation remains host code because
the existing generator uses Kotlin/IDE APIs. Requests the GUI cannot represent are kept in linked scripts and executed
through Follow Script. The dialog reads inputs and prepares previews in a background worker, invalidates stale results,
and commits only an explicit, current selection through `RequestImportCoordinator` and destination writers.

The environment writer adds non-conflicting profiles without changing the active selection itself. After a successful
commit, the dialog applies the activation choice; it defaults to the sole new profile only when no environment is selected.
Static aliases are resolved; unresolved values stay absent with diagnostics. Adding a format requires a parser and registry entry, plus explicit
model/adapter changes and checks when it introduces new request semantics. Import does not execute the source command,
Postman scripts or HTTP requests.

## Responses and persistence

The runtime captures request/response metadata, saves response files and provides a bounded UI preview. The saved
file retains the full body independently of the preview. CLI structured results and IDEA history retain the file
association even if an assertion fails after the HTTP response arrives.

Project GUI drafts, generated scripts and Quick responses live under `.kttp/`; editor Run/Follow responses and the
shared history live under the IDE configuration's `consoles/Kttp/responses/`. History stores absolute paths and renders
shortened paths for display. See the complete [GUI storage and lifecycle rules](gui.md).

## Boundaries that matter

- GUI Quick Request sends a represented HTTP model; it does not evaluate script callbacks or client configuration.
- Full script execution has JVM startup and Kotlin compilation overhead; HTTP-only timing does not measure it.
- A script's environment, files, delegated storage and cookie session depend on runner configuration.
- Release packages and the documentation site are built from the same tagged source after CI checks pass.
