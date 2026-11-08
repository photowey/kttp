# Kttp 文档站

[English](README.md) | [简体中文](README.zh_CN.md)

文档站使用 MkDocs Material 和 static i18n，提供英文、简体中文、本地搜索及浅色和深色主题。
配色采用 Kotlin 风格的紫色、粉色和橙色。
Markdown 源文件位于 `docs/`，依赖锁定在 `uv.lock` 中。生成的 HTML 和 Python 环境不纳入 Git。

## 本地预览

安装 Python 3.12 和 [uv](https://docs.astral.sh/uv/)，然后从仓库根目录执行：

```bash
cd docs/design
uv sync --locked
uv run --locked mkdocs serve
```

打开 <http://127.0.0.1:8000/kttp/>，通过页头切换语言；中文页面位于 `/kttp/zh/`。

## 构建

在 `docs/design/` 中执行：

```bash
uv run --locked mkdocs build --strict
```

输出位于 `site/`。无效链接和其他 MkDocs 警告会使 CI 失败。验证自定义域名或路径时执行：

```bash
KTTP_DOCS_SITE_URL=https://example.com/manual/ uv run --locked mkdocs build --strict
```

## 发布

CI 在 main 分支推送和 Pull Request 中构建文档。推送与项目版本匹配的稳定版 `vX.Y.Z` 标签后，流程会运行检查，
将插件、CLI、文档归档和校验文件发布到 GitHub Releases，再部署同一标签的 Pages。
main 分支推送不会更新线上站点。这些工作流不会上传到 JetBrains 插件市场。

首次部署前，在 **Settings → Pages → Build and deployment → Source** 中选择 **GitHub Actions**。
如果 `github-pages` 环境启用了部署限制，请允许匹配 `v*` 的 **Tags**。
如果从 main 分支手动重试，也需要允许 `main` **Branch**。
重试时运行 **Docs Pages**，明确填写已经发布的稳定版 `release_tag`。

标签与版本校验、发布说明、产物和重试方式见[开发与发布](docs/development.zh.md)。

## 编辑约定

- 每个页面同时提供英文 `name.md` 和中文 `name.zh.md`，并加入 `mkdocs.yml` 的两套导航。
- 可执行示例、路径和界面名称应与当前实现保持一致。
- 页面之间使用相对 Markdown 链接，让两种语言生成各自正确的 URL。
- 在 `docs/assets/stylesheets/kotlin.css` 中修改配色，并保持两种主题下的对比度。
- 提交前执行严格构建，不提交 `site/` 或 `.venv/`。

可从[英文手册](docs/index.md)或[简体中文手册](docs/index.zh.md)开始阅读。
