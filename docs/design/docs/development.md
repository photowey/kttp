# Development and releases

## Local development

Import the repository root as a Gradle project and use JDK 21. The Gradle Wrapper pins Gradle 8.13 and checks the
official distribution's SHA-256. Keep machine-specific settings in your own `~/.gradle/gradle.properties`.

```bash
./gradlew test
./gradlew :idea:kttp-idea:buildPlugin
./gradlew :core:kttp-cli:shadowJar
./gradlew :idea:kttp-idea:runIde
```

`runIde` starts a development IDEA with the plugin installed. Its sandbox is under
`idea/kttp-idea/build/idea-sandbox/`. The build targets IDEA 2025.3.2, `sinceBuild=253.0`. Core JVM bytecode targets
and the plugin's JDK 21 target are configured separately. Compatibility with every IDE product and third-party theme
has not been verified.

## Documentation

Install Python 3.12 and uv, then run from the repository root:

```bash
cd docs/design
uv sync --locked
uv run --locked mkdocs serve
```

Preview the site at `http://127.0.0.1:8000/kttp/`. For a strict production build:

```bash
cd docs/design
uv sync --locked
uv run --locked mkdocs build --strict
```

Run either block from the repository root. Build output goes to `docs/design/site/`. Each English `.md` page has a
Chinese `.zh.md` counterpart, served under `/zh/`; keep their examples and behavior descriptions consistent.
The site uses Kotlin-inspired purple, pink and orange in light and dark themes.

## GitHub workflows

- **CI** runs for `main`, pull requests and manual dispatch, and is reusable by releases. It tests the Gradle modules,
  builds the plugin ZIP and CLI fat JAR, validates distribution contents and builds the documentation strictly.
- A pushed **stable `vX.Y.Z` tag** starts release validation. Its version must match Gradle's `project.version` with
  the existing `-SNAPSHOT` suffix removed, following the build's artifact naming rule. Leading zeroes and prerelease
  suffixes are not accepted. For example, `1.0.0-SNAPSHOT` produces artifacts for tag `v1.0.0`.
- Release validation requires nonempty English notes at `docs/releases/vX.Y.Z.md`. The tagged source must pass
  reusable CI before publication.
- A successful release publishes the assets below, then calls **Docs Pages** for that same published stable tag.
  A `main` push validates the site without deploying production Pages.

| Release asset | Purpose |
| --- | --- |
| `kttp-idea-X.Y.Z.zip` | Installable IDEA plugin, including `cli/kttp-cli.jar` |
| `kttp-cli-X.Y.Z.jar` | Standalone CLI with dependencies |
| `kttp-docs-X.Y.Z.tar.gz` | Built documentation site for the released source |
| `SHA256SUMS` | SHA-256 digests of the release assets |

The release workflow does not create tags and does not publish to JetBrains Marketplace.

## Configure Pages before the first release

1. In repository **Settings → Pages**, set **Source** to **GitHub Actions**.
2. Check the `github-pages` environment's deployment branch/tag rules. Allow **Tags** matching `v*`. If manual
   retries run from `main`, also allow `main` as a **Branch**. A tag-triggered deployment inherits the tag ref.
3. Allow the workflow's declared token permissions. The release publisher needs `contents: write`; Pages deployment
   needs `pages: write` and `id-token: write`. The normal CI jobs use read permissions.

These workflows use GitHub's automatic `GITHUB_TOKEN`; no JetBrains publishing token or custom signing secret is
required. Environment protection rules may require a repository owner's approval before deployment.

## Publish a release

1. Choose a stable version matching `project.version` after the normalization described above.
2. Write and commit `docs/releases/vX.Y.Z.md`, and run the tests, plugin build and strict documentation build.
3. Commit the reviewed source and ensure CI passes. Replace the example tag below with your intended release:

   ```bash
   KTTP_TAG=v1.0.0
   git tag -a "$KTTP_TAG" -m "Release $KTTP_TAG"
   git push origin "$KTTP_TAG"
   ```

4. Check the tag's workflow, asset list and checksums, then confirm its Pages deployment succeeds.
5. Install the released plugin ZIP into a clean supported IDEA instance and inspect the live English/Chinese site.

To retry documentation deployment, run **Docs Pages** manually with an explicit `release_tag` for an existing,
published, non-prerelease GitHub Release. It checks out that tag rather than the default branch. Choose an older tag
only when intentionally rolling back the documentation. Before the first release, use the local preview.

Checksums detect differences between downloaded assets and the manifest; download both from the intended trusted
release. Actions permissions, Pages approvals and public deployment must be checked on GitHub, separately from
local tests.
