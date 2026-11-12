# Agent Skill

The `kttp` Agent Skill provides syntax and execution guidance for writing, explaining and debugging `.kttp.kts`
scripts, including the differences between script execution and Quick Request.
The skill does not install the IDEA plugin, CLI or JDK.

These instructions describe Kttp 1.1.0: IDEA 2026.3 (builds `263.0` through `263.*`), JDK 25 for development, and
JDK 21 or 25 for scripts. They cover native delegated request results, cURL export boundaries and custom methods in
Follow Script. When using an older runtime, check its API before applying version-specific guidance.

An agent can translate pasted cURL into Kotlin source using this skill. The plugin has no cURL import UI in 1.1.0.

## Install from GitHub

Install Node.js and npm using a Node.js version supported by the Skills CLI. Once the repository is published, run:

```bash
npx skills add photowey/kttp
```

To select the skill explicitly:

```bash
npx skills add photowey/kttp --skill kttp
```

The CLI lets you choose a target agent and installs to the current project by default. Add `-g` for a user-level
installation. Check the CLI's runtime requirements for the version you use.

## Preview a source checkout

From the Kttp repository root:

```bash
npx skills add . --list
npx skills add . --skill kttp
```

The first command lists discovered skills without installing them. Use these commands to check local changes before
publishing; the GitHub form reads the published revision.

## What it covers

| Reference | Topics |
| --- | --- |
| `skills/kttp/SKILL.md` | Workflow, syntax boundaries and choosing execution paths |
| `skills/kttp/references/syntax.md` | Request builders, responses, environment schemas, files and client configuration |
| `skills/kttp/references/recipes.md` | JSON, forms, multipart, delegated login and Kotlin reuse |
| `skills/kttp/references/execution.md` | IDEA/CLI execution, GUI modes and debugging |

The references are included in the skill installation. In a source checkout, these paths are relative to the
repository root. See [Kotlin scripting](scripting.md) and the [CLI reference](cli.md) for the online introduction.

## Example requests for your agent

- “Write a Kttp POST request with a JSON body and check the status and returned id.”
- “Convert this cURL command to a `.kttp.kts` script using my existing environment profile.”
- “Explain why this script works in Follow Script but cannot use Quick Request.”
- “Compile this script without sending its HTTP requests, and report what was actually verified.”

Provide the API contract, required environment keys and runtime location. Ask the agent to distinguish generated code
from scripts it has compiled or executed. Compile-only checks cover syntax and types; they do not validate endpoint
responses, environment values or runtime files.
