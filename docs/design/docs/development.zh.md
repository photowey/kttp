# 开发与发布

## 本地开发

将仓库根目录作为 Gradle 工程导入，并使用 JDK 25。Gradle Wrapper 固定为 Gradle 9.7.0，
同时校验官方发行包的 SHA-256。与本机相关的设置请放在个人的 `~/.gradle/gradle.properties` 中。

```bash
./gradlew test
./gradlew :idea:kttp-idea:buildPlugin
./gradlew :core:kttp-cli:shadowJar
./gradlew :idea:kttp-idea:runIde
```

`runIde` 会启动安装当前插件的开发 IDEA 实例，沙箱位于 `idea/kttp-idea/build/idea-sandbox/`。
构建目标为 IDEA 2026.3，`sinceBuild=263.0`；Core 字节码目标与插件的 Java 25 字节码目标分别配置。
尚未逐一验证所有 IDE 产品和第三方主题的兼容性。

## 文档

安装 Python 3.12 和 uv，然后在仓库根目录运行：

```bash
cd docs/design
uv sync --locked
uv run --locked mkdocs serve
```

在 `http://127.0.0.1:8000/kttp/` 查看预览。严格生产构建：

```bash
cd docs/design
uv sync --locked
uv run --locked mkdocs build --strict
```

两个代码块均从仓库根目录开始执行，构建输出位于 `docs/design/site/`。
每个英文 `.md` 页面都有对应的中文 `.zh.md` 页面，中文访问路径为 `/zh/`；两种语言的示例和行为说明应保持一致。
站点采用 Kotlin 风格的紫色、粉色和橙色，支持浅色和深色主题。

## GitHub 工作流

- **CI** 在 `main`、Pull Request 和手动触发时运行，也可由 Release 复用。它会测试 Gradle 模块、
  构建插件 ZIP 和 CLI fat JAR、检查分发包内容，并严格构建文档站。
- 推送**稳定版本标签 `vX.Y.Z`** 会启动 Release 校验。标签版本须与去掉 `-SNAPSHOT` 后缀的
  Gradle `project.version` 一致，沿用构建产物的命名规则。不接受前导零和预发布后缀。
  例如 `1.0.0-SNAPSHOT` 对应标签 `v1.0.0`。
- Release 校验要求 `docs/releases/vX.Y.Z.md` 包含非空英文发布说明。标签对应的源代码必须通过复用 CI，才能发布。
- Release 成功发布产物后，使用同一稳定标签调用 **Docs Pages**。
  推送 `main` 会验证站点，但不会部署生产 Pages。

| Release 资产 | 用途 |
| --- | --- |
| `kttp-idea-X.Y.Z.zip` | 可安装的 IDEA 插件，包含 `cli/kttp-cli.jar` |
| `kttp-cli-X.Y.Z.jar` | 包含依赖的独立 CLI |
| `kttp-docs-X.Y.Z.tar.gz` | 根据该 Release 源代码生成的文档站 |
| `SHA256SUMS` | Release 文件的 SHA-256 摘要 |

Release 工作流不会创建 Git 标签，也不会发布到 JetBrains Marketplace。

## 首次发布前配置 Pages

1. 在仓库 **Settings → Pages** 中将 **Source** 设置为 **GitHub Actions**。
2. 检查 `github-pages` 环境的分支/标签部署规则。允许匹配 `v*` 的**标签**。
   如果需要从 `main` 手动重试部署，还要允许 `main` **分支**。
   标签触发的部署会继承标签 ref。
3. 确认工作流声明的 Token 权限可用。Release 发布任务需要 `contents: write`；Pages 部署需要
   `pages: write` 和 `id-token: write`；普通 CI 任务只需读取权限。

工作流使用 GitHub 自动提供的 `GITHUB_TOKEN`，不需要 JetBrains 发布令牌或自定义签名密钥。
环境保护规则可能要求仓库所有者批准部署。

## 发布 Release

1. 选择与归一化后 `project.version` 一致的稳定版本。
2. 编写并提交 `docs/releases/vX.Y.Z.md`，运行测试、插件构建和严格文档构建。
3. 提交已审查的源代码，并确认 CI 通过。将下面示例替换为实际版本：

   ```bash
   KTTP_TAG=v1.0.0
   git tag -a "$KTTP_TAG" -m "Release $KTTP_TAG"
   git push origin "$KTTP_TAG"
   ```

4. 检查标签对应的工作流、产物列表和校验和，确认 Pages 部署成功。
5. 在干净的受支持 IDEA 实例中安装 Release 插件 ZIP，并检查线上中英文站点。

重试文档部署时，手动运行 **Docs Pages**，并明确填写某个已发布、非预发布的 GitHub Release 的
`release_tag`。工作流会检出该标签，而不是默认分支。只有打算回滚文档时才选择较早标签。
首次发布前请使用本地预览。

校验和用于检查下载文件是否与清单一致，文件和清单都应从预期的可信 Release 获取。
Actions 权限、Pages 环境审批和公网部署需要在 GitHub 上单独检查，本地测试不能替代这些检查。
