---
hide:
  - toc
---

# HTTP requests, written in Kotlin

**Kttp** is an HTTP client built around Kotlin scripts, with a command-line runner and an IntelliJ IDEA plugin.
Write requests in `.kttp.kts` files, reuse Kotlin code, check responses, or compose a request in the Kttp tool window.

[Get started](quick-start.md){ .md-button .md-button--primary }
[Explore the GUI](gui.md){ .md-button }

```kotlin
GET("https://httpbin.org/get", name = "hello-kttp") {
    query("source", "kttp")
}.then {
    assertStatus(200)
    assertEquals("kttp", decode<String>("$.args.source"))
}
```

## Choose an execution path

| Entry point | Behavior | Suitable for |
| --- | --- | --- |
| Script Run / CLI | Evaluate Kotlin and execute registered requests with their callbacks | Script workflows, environment values and reusable Kotlin |
| GUI **Follow Script** | Use the shared CLI/JDK launch path for the selected script request | Keeping GUI execution consistent with the source script |
| GUI **Quick Request** | Send the represented HTTP request directly | Independent requests without Kotlin evaluation or `then` |

Follow Script preserves script behavior; Quick Request skips JVM startup and script compilation.
See the [execution rules](gui.md) before switching modes.

## Find your guide

- [Quick start](quick-start.md): build or install the plugin and run a first request.
- [Kotlin scripting](scripting.md): request builders, assertions, environment profiles and local files.
- [GUI requests](gui.md): drafts, script links, responses, history and execution limits.
- [CLI reference](cli.md): compile, execute and select requests outside the IDE.
- [Agent Skill](agent-skills.md): teach a coding agent Kttp's actual syntax and workflow.
- [Architecture](architecture.md): the DSL, CLI and IDEA integration boundaries.
- [Development and releases](development.md): local checks, documentation and the tag-to-Release/Pages process.

## Current scope

The plugin targets IntelliJ IDEA 2026.3 with `sinceBuild=263.0`. Use JDK 25 for development; JDK 21 or 25 is recommended
for script execution. Other IDE products and third-party themes have not been independently verified. HTTP-file and
Postman import actions are placeholders.

Each documentation build describes its source revision. The live Pages site follows published stable releases;
pushes to `main` validate the site without deploying it.
