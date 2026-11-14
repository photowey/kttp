# GUI requests

Open **View → Tool Windows → Kttp**. The sidebar lists drafts, scripts and response files. The main area contains
the request composer and the **Response**, **History** and **Assertions** views.

New GUI requests start with an editable example. Nothing is sent until you click **Send**:

```kotlin
GET("https://httpbin.org/get") {
    query("source", "kttp")
    header("Accept", "application/json")
}
```

The **Sources** groups refer to different storage scopes:

- **Drafts** contains saved GUI requests. Opening a script does not create a saved draft.
- **Project** finds `.kttp.kts` scripts across the current project's content roots, including its modules. It is not
  limited to `.kttp/`; IDE-excluded directories and files ignored by version control are omitted.
- **Kttp** lists scripts in the running IDE's `consoles/Kttp/` directory. Another IDE installation or development sandbox
  can use a different configuration directory.

On first opening in a project, Kttp expands a narrow docked tool window within the available IDE width and gives the
navigation a useful initial width. Later manual resizing and saved splitter proportions take precedence.

## Import cURL or Postman

In a script editor, choose **+ → Import Requests…** to insert selected requests into the current script. When the caret
is inside a top-level request or declaration, insertion follows that declaration. If the imported code reuses existing
script properties declared later, insertion moves past those declarations so they are initialized first. Existing code is preserved; the
script edit uses an IDE write command and supports editor Undo. In the GUI **Sources** area, **Import Requests…**
creates saved GUI drafts. Both entries use the same themed dialog: sources on the left, request selection above script
and diagnostic tabs on the right. **Destination** and **Environment** sit below the corresponding columns and share
their column divider, keeping both rows aligned when resized. Target labels adapt to the IDE font;
the destination summary shows full paths on hover. Status and Import/Cancel actions remain below these settings.
Drag the dividers to adjust widths and preview height.
After a GUI import, Sources clears its search/filter, expands Drafts, selects the first new request and shows the import count.

1. Choose **cURL** or **Postman** in **Format**. Source fields accept **Text** with **Paste**, or a UTF-8 **File**.
2. For Postman, supply a Collection and optionally an Environment. Leave Collection empty to import only an Environment;
   this does not modify the current script.
   **Base directory** defaults to the current project for relative uploads. Clear it to fall back to the selected source
   file's directory. Browse starts at an existing source path, the selected base directory or the current project.
3. Click **Refresh Preview**. Review the request names, methods, URLs and source paths, select the requests to import,
   and read **Generated Script**, **Diagnostics** and **Retained Source**. Parsing and preview run in the background;
   **Cancel Preview** stops preview preparation.
4. Check the displayed target and click **Import** to write the selection. **Cancel** closes the dialog without changing
   scripts, drafts or environments. Input, format or target changes invalidate the preview and require a refresh.

Import never executes curl, a shell, JavaScript or HTTP requests. Fatal input errors block import; request errors
prevent selecting the affected row. Requests with unsupported scripts or options needing review start unchecked.
Selecting one explicitly accepts its static fields only. Retained unsupported source is saved in an `.import-report.txt`
file beside the import target for later review; it does not become executable Kotlin or assertions.

### Supported input and limits

| Input | Imported fields | Boundaries |
| --- | --- | --- |
| cURL | One HTTP(S) request; method, URL/query, repeated headers, static cookies, Basic/Bearer credentials, text/JSON, URL-encoded data, multipart and supported file references | POSIX quotes, escapes and backslash continuations; unknown options, shell expansion, multiple transfers and unsupported combinations are rejected |
| Postman Collection v2.0/v2.1 | Nested folders in source order, names/source paths, string or structured URLs, enabled query/header/body fields, raw text/JSON, urlencoded, form-data and file references | Import selected requests; disabled fields are omitted. Postman v1, workspace backups and cloud sync are outside this importer |
| Postman authentication | Inherited No Auth, static Basic/Bearer and API key in a header or query | Request settings override the closest folder, outer folders and Collection. OAuth flows, signing and other auth types need manual conversion |
| Postman scripts/variables | Static variable references and diagnostics with source paths; unsupported source retained for review | Pre-request/test JavaScript is not executed or translated; dynamic variables and script-generated values require manual work |

cURL import does not interpret arbitrary Bash, cmd or PowerShell programs. It accepts a specific option set, rather
than all curl flags. Some response-display/output flags are omitted with an informational diagnostic; `--compressed`
requires review because the target client's compression behavior can differ. File-backed data is accepted for
`--data-binary @path` and `--json @path`; file transformations such as `--data @path`, stdin and cookie files are
rejected. Literal `{` or `}` in URLs are rejected even with `--globoff`, because Kttp treats braces as path parameters;
use `%7B` and `%7D` when literal braces are intended. Review diagnostics before accepting a conversion.

For cURL multipart files without an explicit `;type=`, supported filename-to-MIME mappings are retained. An unknown file
extension falls back to `application/octet-stream` with a warning requiring explicit request selection. Set `;type=`
when the part needs a different content type; import does not inspect file bytes to infer one.

Uploaded files are retained as references. Import resolves literal relative paths syntactically; it does not read or
check the upload contents. The generated request reads them when explicitly executed. Pasted commands with relative
paths use the displayed base directory (the current project by default); verify files and runtime access before sending.
Multipart filenames default to the resolved upload path's final component, including paths supplied by environment
variables at execution. An explicit imported filename takes precedence.

GET and HEAD bodies are unsupported by Kttp/OkHttp. In Postman imports, these bodies and variable references in
header, query, form/multipart field names or API-key names produce per-request errors. The affected row cannot be
selected; valid sibling requests remain importable. Convert those names to static text or remove the body in the source.
Variable references in supported field values keep the behavior described below.


**Import into** preserves the entry default: **Current Script** in the editor and **GUI Drafts** in the sidebar.
Choose **New Script** to create one grouped file under `<project>/.kttp/imports/`, with an editable directory and file name
based on the Collection name. Name collisions receive numeric suffixes; existing files are not overwritten.
The imported environment is written beside that script. Environment-only imports do not create empty scripts.
Mixed Postman values generate ordinary Kotlin string templates and shared `String by env` declarations; compatible
existing delegates are reused, while conflicting declarations require review.

**Use imported environment** defaults on only when there is one created profile and no current selection.
Otherwise the existing selection is retained unless you opt in; multiple profiles require a choice.
The selection changes only after successful import, using the actual name such as `postman_dev-2`.
cURL imports do not create empty environment profiles.

### Variables and environments

Environment is optional. Collection variables supply defaults, and enabled Environment entries override matching keys.
Folder and URL variables remain scoped to their requests. `{{name}}` becomes a Kttp value/environment reference;
missing keys remain references with diagnostics and fail under the existing missing-environment rules when used.
They are not replaced with empty strings. Dynamic Postman variables require manual conversion.

Import adds `postman_<environment>` profiles to `kttp.env.json`, using numeric suffixes when a name already exists.
Collection defaults without an Environment use `postman_<collection>`. Existing profiles are preserved. Static nested aliases are resolved before writing. Undefined, dynamic or cyclic
profile values are omitted with warnings so unresolved `{{tokens}}` do not become executable defaults. Review the import
result for created profile names and the activation choice before execution.

Variable expansion memoizes shared aliases. Each expanded value is limited to 1,048,576 UTF-16 code units; cached
alias text and combined imported profile values each have a limit of 8,388,608 code units. A resolver allows at most
10,000 substitutions and 64 nested references. Size or substitution limits abort import before files are written;
unresolved, cyclic or excessively nested environment values are omitted. Environment conversion runs in the dialog's
background worker, and a concurrent change to the target environment file requires refreshing the preview.

Import commits and GUI script preparation finish their local writes through synchronous VFS operations. This prevents
the external CLI from reading an older file while IDEA 263 has a document save queued. IDEA's save transformations,
encoding and line separators are retained; no experimental flush API is used.

For script imports, profiles are written beside the target script. GUI imports use the running IDE's `consoles/Kttp/`
directory for environments and script-only requests. For an environment-only import, **Choose Environment Folder…**
lets you select the destination. Existing environment lookup still applies: a script-local `kttp.env.json` takes
precedence over the console file, without merging the two.

### GUI drafts and linked scripts

Requests the composer can represent become editable, saved drafts and support **Quick Request** and **Generate Script**.
Custom methods, multipart/file bodies and mixed variable expressions needing Kotlin use a linked script and default to
**Follow Script**. Their semantics are preserved in that script; Quick Request is unavailable when the composer cannot
represent them. Edit the linked script through **Open Script**, then use **Follow Script → Send** to execute it.

Generated environment URL concatenations display as `${baseUrl}/path` in the composer and script request tree.
This is a static display; variables are resolved when the script runs. Other computed URLs show **Script URL (open source
to edit)**. These requests remain read-only and require Follow Script.

## Execution mode

| Mode | What runs | Default for |
| --- | --- | --- |
| **Follow Script** | The selected registration in the source script, through the same CLI/JDK launch path as script Run | Imported script requests |
| **Quick Request** | The HTTP data represented by the composer, directly through the GUI executor | New drafts |

Follow Script evaluates the source file, executes the selected request and any request dependencies, and runs callbacks
such as `then`. Top-level Kotlin code also runs during evaluation. Use this mode for dynamic expressions, authentication
workflows, client configuration and response processing. The source must compile and evaluate successfully.
Follow Script has no overall GUI execution timeout; HTTP timeouts remain controlled by the script's client settings.
Use Cancel to stop an active script process.

Quick Request sends only the HTTP data the GUI can represent, skipping Kotlin evaluation, script-level configuration
and `then`. It uses a 60-second call timeout and does not maintain a persistent cookie jar. A saved per-request mode
may override the initial default.

## Edit, save and link a script

Imported script requests remain read-only in the composer in either mode. Edit the source script, or use **Detach**
on a supported projection to create an editable draft.

| Action | Effect |
| --- | --- |
| **New Draft** | Create an in-memory editable request in Quick Request mode |
| **Save Draft** | Persist the working draft without immediately rewriting a script |
| **Generate Script / Update Script** | Write generated Kotlin, save the draft and record its file association |
| **Open Script** | Open the linked source or generated file |
| **Detach** | Copy projected HTTP data to an editable draft and switch to Quick Request; leave the source unchanged |
| **Send / Cancel** | Execute the chosen mode, or cancel its active HTTP call/script process |

If a generated file changed manually, including unsaved editor changes, generation chooses a new file instead of
overwriting those changes. Follow Script Send prepares a generated script for a GUI draft. For an imported request,
it saves and executes the current source document without replacing it with generated code.

Detach copies only the projected HTTP model. To retain the original Kotlin logic and callbacks, run the source request
with Follow Script. Use Save Draft to keep the detached draft.

## Fields and sidebar filters

Use **Params**, **Headers**, **Body** and **Auth** for supported request fields. GUI environment references use
`${baseUrl}` as an entire field value; `${baseUrl}/users` is not GUI string interpolation. Scripts use normal
Kotlin interpolation instead. JSON/Raw body fields also accept `@file:path/to/body.json`.
Legacy `${env:name}` input is accepted and redisplayed as `${name}`. Prefix the entire input with `\` to send literal
text, for example `\${API_TOKEN}` sends `${API_TOKEN}` without resolving it. Saved literal values keep their type;
reopening adds the escape when needed. The Sources hint follows the parsed value, including literal escapes.

Detached drafts retain their source environment lookup location in both GUI execution modes. Generating a script
for Follow Script does not switch that lookup to the generated file's directory. Imported scripts still use their
own environment location; independent drafts without a file link use Consoles/Kttp.

Script file rows show the total request count. Request labels describe Quick Request support:

- **Quick available** covers both complete and HTTP-only projections. Quick Request may still omit script processing;
  hover to see editing limits and omitted behavior, and use Follow Script when that behavior is needed.
- **Script required** means the request needs Follow Script. It can still run from the GUI.

Custom methods such as `request("CUSTOM", url)` use Follow Script. The composer shows **Script** for a method it
cannot represent; Quick Request, detaching and GUI script generation remain unavailable for that request.

These labels do not indicate the selected execution mode. The **Quick support** filter selects script files by full
projection, HTTP-only projection, script-required or mixed categories without switching modes. The search box filters
by draft title or script file name.

## Responses and history

The **Response** view shows the latest GUI Send result. Script Run writes text to the IDEA **Run** window;
its saved files appear in **Responses** and **History**. In **Run**, click the response path to open it in the editor,
or use its context menu to reveal it in the system file manager.

During Send, an animated indicator and elapsed time remain visible until execution or cancellation completes.
A response preview can appear while the full file is being saved and script callbacks are still running. It remains
marked as running until execution finishes, when final assertions, timing and history are updated. HTTP 200 in a
preview does not establish final execution success or a completed file save. Execution may still be cancelled or fail
during assertions or persistence.

Preview capture reads at most 64 KiB of decoded body data, waiting until that amount is available or the body ends.
Receiving headers alone does not trigger the preview, and the body is not continuously streamed into the viewer.
The viewer takes the first 50,000 characters before optional JSON formatting. Open the saved file for the full body.
If the script's live-preview observer is unavailable, the GUI still delivers the final execution result.

The response header groups status, timing and file actions. File actions become available once the saved file's path
is known. File refresh and file-manager launch run in the background; Copy Path uses the IDE clipboard.
Drag the dividers to adjust navigation width, parameter/script panel widths, and composer/result heights.

History is shared by projects using the same IDE configuration directory. Rows display shortened file paths while
file operations retain absolute paths. Double-click a row to open its response file. There is currently no Re-run
action in History.

| Location | Contents |
| --- | --- |
| `<project>/.kttp/requests/` | GUI-generated scripts |
| `<project>/kttp/temp/` | Sidebar Add Request scripts when no script is selected; requires a local project directory |
| `<project>/.kttp/gui-state/drafts.json` | Saved drafts |
| `<project>/.kttp/responses/` | Project Quick Request responses |
| `<IDE config>/consoles/Kttp/responses/` | Script Run/Follow responses, fallback Quick responses and `kttp-history.json` |

Without a project base directory, generated scripts and draft state fall back to the IDE's `consoles/Kttp/` directory.
The configuration path depends on the running IDE.

## Understand timing

**Total** includes preparation, HTTP execution, body download, file saving and result decoding. Follow Script also
includes JVM startup, compilation when needed and script callbacks. **HTTP** measures only the final exchange until
response headers, excluding script compilation and body download.
Follow Script can therefore take seconds to start and compile even when HTTP time is short. Quick Request skips
those phases.

The **HTTP/2** checkbox selects `H2_PRIOR_KNOWLEDGE`, not automatic HTTP/2 negotiation. HTTP-file import remains a
placeholder. See [Kotlin scripting](scripting.md) for behavior the direct GUI model cannot preserve.
