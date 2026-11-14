# Import cURL and Postman

Kttp 1.2.0 provides an IDEA importer. The standalone CLI has no cURL/Postman import flag. When converting a source
outside the IDE, return Kotlin source and its required inputs, and distinguish that work from an IDE import or execution.

## Entry, preview and destination

| Entry | Destination |
| --- | --- |
| Script editor **+ → Import Requests…** | Insert selected requests into the current script after any reused script property declarations; preserve existing code, use editor Undo for the script edit |
| GUI **Sources → Import Requests…** | Saved editable drafts when representable; linked scripts with Follow Script for script-only semantics |

Choose **cURL** or **Postman** in **Format**, paste **Text** or select a UTF-8 **File**, then click **Refresh Preview**.
Review request names, methods, URLs, source paths, target and the **Generated Script**, **Diagnostics** and **Retained
Source** tabs. Select requests before clicking **Import**. **Cancel** leaves scripts, drafts and environments unchanged;
**Cancel Preview** stops preview preparation. Editing input, format or target invalidates the preview.

Fatal source errors block import; an erroneous request cannot be selected. Requests with unsupported scripts or options
requiring review start unchecked. Explicit selection accepts static fields only, without enabling the unsupported
behavior. Retained source fragments are saved in a separate `.import-report.txt` file beside the target.

Import does not execute curl, a shell, Postman JavaScript or HTTP requests. A successful preview is evidence of conversion,
not successful compilation, endpoint behavior, valid credentials, readable upload files or migrated assertions.

## cURL fidelity

The importer accepts one HTTP(S) request in bounded POSIX syntax: single/double quotes, escapes and backslash line
continuations. It does not interpret arbitrary Bash, cmd or PowerShell programs. Shell expansion, command substitution,
operators, multiple transfers and unknown options are rejected. Quote literal URL/data characters that a shell would expand.

Supported request data includes methods/URLs, repeated headers and queries, static Cookie strings, explicit Basic
`--user username:password`, static Bearer tokens/headers, text/JSON, URL-encoded data and supported multipart parts.
Common options include `-X`, `-H`, `-b`, `-u`, `-d`, `--data-raw`, `--data-binary`, `--data-urlencode`, `--json`, `-G`,
`-I`, `-L`, `--globoff`, `-F` and `--form-string`. Acceptance is conditional on the combination preserving request
semantics; do not promise that every combination is supported. Redirects remain disabled unless requested by `-L`.
Literal `{` or `}` in URLs are rejected even with `--globoff`, because Kttp treats braces as path parameters. Use an
explicitly percent-encoded URL (`%7B` / `%7D`) when literal braces are intended.

Response-display/output switches can be omitted with diagnostics. `--compressed` requires review because the target
client does not reproduce curl's advertised compression algorithms. Proxy/TLS setup, cookie files, interactive
password prompts and arbitrary curl switches are not covered by this list and must not be silently dropped.

File-backed data uses `--data-binary @path` or `--json @path`. File transformations such as `--data @path`, stdin,
file data moved into a query and unsupported multipart metadata are rejected. Multipart supports text, individual file
references, content type and filename; complex curl multipart syntax needs manual conversion. Without explicit
`;type=`, supported curl filename-to-MIME mappings are retained. Unknown file extensions default to
`application/octet-stream` with a warning requiring explicit request selection. Set `;type=` for the intended type;
import never inspects file bytes to infer MIME.

## Postman fidelity

Accept Collection JSON v2.0/v2.1 with an optional Environment JSON, or an Environment by itself. Keep nested folder
order, request names and source paths. Enabled query/header/body fields, string/structured URLs, raw text/JSON,
urlencoded, form-data and file references are converted; disabled fields are omitted.

GET/HEAD bodies are unsupported by Kttp/OkHttp. Those bodies and variable references in header, query, form/multipart
field names or API-key names are per-request errors: affected rows cannot be selected, while valid sibling requests
remain importable. Convert those names to static text or remove the body before importing. Supported field values
retain variable references; do not silently import a dynamic field name as literal `{{text}}`.

Authentication inherits from the request, nearest folder, outer folders and Collection. Static No Auth, Basic, Bearer
and API keys in headers/queries are supported. OAuth authorization flows, signatures and other auth types require manual
conversion. Pre-request/test JavaScript is retained for review, never executed or translated into Kotlin. Do not claim
that tests became Kttp assertions. Dynamic Postman variables and values produced by scripts require manual work.
Postman v1, workspace backup archives, cloud synchronization and export to Postman are outside the importer.


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

## Variables, environments and files

- Collection variables provide defaults. Enabled Environment values override matching Collection keys. Folder and URL
  variables stay scoped to their requests rather than becoming one shared global map.
- `{{name}}` becomes a Kttp value/environment reference. Missing keys remain explicit with diagnostics and fail when
  used under the existing environment rules; do not replace them with empty strings or guessed defaults.
- Profiles are added to `kttp.env.json` with `postman_` prefixes and non-conflicting names. Existing profiles are preserved.
  The sole created profile is selected automatically only when no environment is selected; otherwise activation is opt-in.
  Multiple profiles require an explicit choice. Activation uses the final collision-renamed profile and happens only after success. Static nested aliases resolve before writing;
  undefined, dynamic or cyclic values are omitted with warnings rather than writing literal unresolved `{{tokens}}`.
- Script imports write profiles beside the script. GUI imports use `<IDE config>/consoles/Kttp/` for environments
  and script-only requests. Environment-only import offers **Choose Environment Folder…**. A script-local environment
  file takes precedence over the console file; the two are not merged during lookup. Importing only an Environment
  leaves the current script unchanged.
- Literal relative uploads use **Base directory**, defaulting to the current project. Clearing it falls back to the selected
  source file's directory; pasted commands still need a base for relative files. Browse honors the existing source path,
  then the base directory/current project. Import resolves paths syntactically; file content is only read when the
  generated request runs. Validate file paths and runtime access separately.
- Expansion is memoized and bounded: 1,048,576 UTF-16 code units per value, 8,388,608 across cached alias text or
  imported profile values, 10,000 substitutions per resolver and 64 nested references. Oversized or excessive
  replacement work aborts import before writes; unresolved, cyclic or excessively nested environment entries are omitted.
- Multipart filenames default to the resolved upload path's final component, including environment-based runtime
  paths. Preserve explicit filenames. Import does not read the upload to determine its name.
- Imported files and GUI-prepared scripts finish their local VFS writes before completion or CLI execution; IDEA's
  encoding, line separators and save transformations are retained.

Custom methods, multipart/file bodies and mixed variable expressions the composer cannot represent keep linked scripts
and use Follow Script. Ordinary GUI imports become editable drafts with Quick Request and Generate Script. Import never
downgrades a custom method to GET merely to fit the composer.

The resizable dialog places sources on the left and requests/script preview on the right. Its footer groups Destination
and Environment under those same columns, sharing the column divider to stay aligned. Full destination paths
are available on hover, with status/actions below. A completed GUI import
clears sidebar search/filter, reveals the first new request under **Sources → Drafts** and
shows its import count. Generated mixed environment URLs display `${baseUrl}/path` without evaluation; other dynamic
URLs use a script-owned placeholder. These displays do not enable Quick Request or change script ownership.
