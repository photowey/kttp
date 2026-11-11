# GUI requests

Open **View → Tool Windows → Kttp**. The sidebar lists drafts, scripts and response files. The main area contains
the request composer and the **Response**, **History** and **Assertions** views.

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
`${env:baseUrl}` as an entire field value; `${env:baseUrl}/users` is not GUI string interpolation. Scripts use normal
Kotlin interpolation instead. JSON/Raw body fields also accept `@file:path/to/body.json`.

Script file rows show the total request count. Request labels describe Quick Request support:

- **Quick available** covers both complete and HTTP-only projections. Quick Request may still omit script processing;
  hover to see editing limits and omitted behavior, and use Follow Script when that behavior is needed.
- **Script required** means the request needs Follow Script. It can still run from the GUI.

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

The **HTTP/2** checkbox selects `H2_PRIOR_KNOWLEDGE`, not automatic HTTP/2 negotiation. HTTP-file and Postman import
actions are placeholders. See [Kotlin scripting](scripting.md) for behavior the direct GUI model cannot preserve.
