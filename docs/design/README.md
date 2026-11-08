# Kttp documentation site

[English](README.md) | [简体中文](README.zh_CN.md)

The manual uses MkDocs Material and static i18n for English and Simplified Chinese pages, with local search and light
and dark themes. Its palette uses Kotlin-inspired purple, pink and orange. Markdown sources live in `docs/` and
dependencies are locked in `uv.lock`; generated HTML and the Python environment are excluded from Git.

## Preview locally

Install Python 3.12 and [uv](https://docs.astral.sh/uv/), then run from the repository root:

```bash
cd docs/design
uv sync --locked
uv run --locked mkdocs serve
```

Open <http://127.0.0.1:8000/kttp/>. Switch languages in the header; Chinese pages are served under `/kttp/zh/`.

## Build

From `docs/design/`:

```bash
uv run --locked mkdocs build --strict
```

Output goes to `site/`. CI fails on broken links and other MkDocs warnings. To check a custom domain or path:

```bash
KTTP_DOCS_SITE_URL=https://example.com/manual/ uv run --locked mkdocs build --strict
```

## Publish

CI builds the site on main pushes and pull requests. A stable `vX.Y.Z` tag matching the project version runs the release
checks, publishes the plugin, CLI, documentation archive and checksums to GitHub Releases, then deploys Pages from
that tag. Main pushes do not update the live site. These workflows do not upload to JetBrains Marketplace.

Before the first deployment, choose **Settings → Pages → Build and deployment → Source → GitHub Actions**. In the
`github-pages` environment, allow **Tags** matching `v*` if deployment restrictions are enabled. Also allow the `main`
**Branch** if manual deployment retries run from main. For retries, run **Docs Pages** with an explicit, already
published stable `release_tag`.

See [Development and Releases](docs/development.md) for tag/version checks, release notes, artifacts and retry details.

## Editing

- Add each page in English (`name.md`) and Chinese (`name.zh.md`), then add it to both navigation trees in `mkdocs.yml`.
- Keep executable snippets, paths and UI labels consistent with the current implementation.
- Use relative Markdown links between pages so both language builds resolve their own URLs.
- Change colors in `docs/assets/stylesheets/kotlin.css`; maintain contrast in both themes.
- Run the strict build before committing. Do not commit `site/` or `.venv/`.

Start with the [English manual](docs/index.md) or [简体中文手册](docs/index.zh.md).
