# CLI reference

The standalone CLI compiles and evaluates `.kttp.kts` scripts outside IDEA. Use a compatible JDK; JDK 21 is recommended.
Download the fat JAR from a release, or build it with `./gradlew :core:kttp-cli:shadowJar`.
The `-plain.jar` artifact does not include dependencies.

Set `KTTP_CLI_JAR` to the actual fat JAR and `KTTP_WORK_DIR` to a directory containing `requests.kttp.kts` and
`env.json`. These are shell variables for the examples, not Kttp configuration keys. Use a flat string map for
`env.json`, as shown in [Kotlin scripting](scripting.md).

## Execute requests

```bash
java -jar "$KTTP_CLI_JAR" \
  --script "$KTTP_WORK_DIR/requests.kttp.kts" \
  --env-file "$KTTP_WORK_DIR/env.json" \
  --base-dir "$KTTP_WORK_DIR" \
  --response-dir "$KTTP_WORK_DIR/responses" \
  --json-output
```

Without a selector, registered requests execute in order after script evaluation. Add `--request-name read-user` to
select a named registration, or `--request-number 0` to select the first registration. Prefer one selector and unique
names. Selection does not skip top-level Kotlin evaluation, and reading a delegated result may execute dependencies.

## Compile without evaluation

```bash
java -jar "$KTTP_CLI_JAR" \
  --script "$KTTP_WORK_DIR/requests.kttp.kts" \
  --execution-mode COMPILE_ONLY \
  --no-compilation-cache --json-output
```

Compile-only checks syntax and types without evaluating the script body or sending its registered requests.
It does not verify runtime environment values, file existence, endpoint behavior or response assertions.
Dependency annotations such as `@file:DependsOn` may still resolve/download dependencies during compilation.

## Options

| Option | Meaning |
| --- | --- |
| `--script <file>` | Required script file |
| `--request-name <name>` | Select an explicit name or delegated-property coordinate |
| `--request-number <n>` | Zero-based registration index; different from the IDE's displayed ordinal |
| `--env-file <json>` | Flat `Map<String, String>`; not IDEA named profiles |
| `--env-param key=value` | Repeat to override environment-file entries |
| `--base-dir <directory>` / `-B` | Relative `localFile` base; otherwise inferred from the script directory |
| `--response-dir <directory>` | Response output directory; defaults to `.kttp-responses` under system temp |
| `--storage-dir <directory>` | Persistent values; omitted means per-process in-memory storage |
| `--cookies-file <file>` | Persistent cookie jar; omitted means no cookie session store |
| `--execution-mode COMPILE_ONLY` | Compile without evaluating the script body |
| `--execution-mode CURL` | Generate cURL without request HTTP transport; still evaluate Kotlin |
| `--no-compilation-cache` | Force compilation rather than reusing cached compilation |
| `--json-output` / `-j` | Print a final structured result |
| `--debug-log` / `-d` | Enable debug logging |
| `--jvm-target <version>` | Script compilation target; defaults to `1.8` |

The IDE also passes `--request-lines first:last`, response manifest and response snapshot options for integration.
Source-line selection takes precedence over a valid numeric selector, which takes precedence over a name. For normal
CLI use, prefer the public name or number selectors above. There is no `--env` switch for selecting an IDEA
environment profile.

## Results and failure handling

Check both the process exit code and the final result's `success`/`error`. Compilation errors and failed callbacks or
assertions cause execution to fail. An HTTP 4xx/5xx response alone does not; assert the expected status in the script.

`--json-output` does not guarantee that stdout contains a single JSON document. Script output and compile messages
may precede the final JSON result line. Preserve those lines when diagnosing a failure.

Persistent storage can reuse a delegated result by property name; omit `--storage-dir` when testing a fresh login.
IDEA script Run and GUI Follow Script currently do not forward persistent-storage or cookie-file options through
their shared invocation model. Quick Request has its own [execution limits](gui.md).
