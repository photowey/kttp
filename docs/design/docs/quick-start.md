# Quick start

## Install the IDEA plugin

Use JDK 25 for development. The plugin targets IntelliJ IDEA 2026.3 and depends on the Kotlin plugin and the platform
language/JSON modules declared in `plugin.xml`. Kttp 1.1.0 declares builds `263.0` through `263.*`; use a compatible
older plugin such as Kttp 1.0.1 for earlier IDE versions.

When a [GitHub Release](https://github.com/photowey/kttp/releases) is available, download its
`kttp-idea-X.Y.Z.zip`. In IDEA, open **Settings → Plugins → Install Plugin from Disk…**, select the ZIP and follow
the restart prompt. This ZIP includes the CLI needed for script execution.

To build from source, run from the repository root:

```bash
./gradlew :idea:kttp-idea:buildPlugin
```

Install `idea/kttp-idea/build/distributions/kttp-idea.zip` through the same dialog. The first build needs network
access for Gradle, dependencies and the IntelliJ Platform. Use the repository's Gradle Wrapper; no developer-specific
Maven repository or home directory is required.

## Run your first script

1. Configure a project JDK; JDK 21 or 25 is recommended.
2. Choose **File → New → Kttp Script**. The new file is created under
   **Scratches and Consoles → Consoles → Kttp**. A project-local `.kttp.kts` file also works.
3. Replace the file contents with this script:

```kotlin
GET("https://httpbin.org/get", name = "first-request") {
    query("source", "kttp")
}.then {
    assertStatus(200)
    val source: String = decode("$.args.source")
    println(source)
}
```

The example needs access to `httpbin.org`. Replace the endpoint and expected response when testing your own service.

Click the Run icon beside the request. The **Run** tool window contains script output; Kttp's **Responses** tree and
**History** provide the saved response file. **Run All Requests** executes the file's registered requests in order.
The editor's **Execute Kttp Request** action defaults to the whole file unless its calling context supplies a request
selection; use the gutter Run icon when selecting one request.

## Send from the GUI

Open **View → Tool Windows → Kttp** and click **New Draft**. The draft already contains `GET https://httpbin.org/get`,
query `source=kttp` and header `Accept: application/json`. Review or change the example, keep **Quick Request** selected,
and click **Send**. Opening a draft does not send or save it.

Use **Save Draft** to keep the request. **Generate Script** writes a linked `.kttp.kts` file and saves the draft.
Switch to **Follow Script** to run in script context. Requests selected from an existing script default to Follow Script
and are read-only in the composer. To edit one, change the source script or use **Detach**.

See [GUI requests](gui.md) for the behavior of each button and the differences between the two modes.

## Use the standalone CLI

The release artifact `kttp-cli-X.Y.Z.jar` includes dependencies. To build it from source:

```bash
./gradlew :core:kttp-cli:shadowJar
```

Set `KTTP_CLI_JAR` to that JAR and save the example above as `first.kttp.kts`:

```bash
java -jar "$KTTP_CLI_JAR" --script first.kttp.kts --json-output
```

The `-plain.jar` build artifact lacks dependencies. See the [CLI reference](cli.md) for response directories,
request selection and compile-only checks.
