# Agent Skill

`kttp` Agent Skill 为编写、解释和调试 `.kttp.kts` 脚本提供语法与执行指南，
包括脚本执行与 Quick Request 的区别。
安装 Skill 不会安装 IDEA 插件、CLI 或 JDK。

这些说明对应 Kttp 1.1.0：IDEA 2026.3（构建号 `263.0` 至 `263.*`），开发使用 JDK 25，
脚本执行推荐使用 JDK 21 或 25。内容包含原生请求结果委托、cURL 导出边界和自定义方法的 Follow Script 执行。
使用旧运行时时，应先核对其 API，再采用与版本相关的说明。

Agent 可以根据该 Skill 将粘贴的 cURL 转换为 Kotlin 源码；1.1.0 插件没有 cURL 导入界面。

## 从 GitHub 安装

安装 Node.js 和 npm，Node.js 版本须满足 Skills CLI 的要求。仓库发布后运行：

```bash
npx skills add photowey/kttp
```

也可以明确选择该 Skill：

```bash
npx skills add photowey/kttp --skill kttp
```

CLI 支持选择目标 Agent，默认安装到当前项目。使用 `-g` 可安装到用户级目录。
运行环境要求以所用 CLI 版本的说明为准。

## 预览源代码仓库

在 Kttp 仓库根目录运行：

```bash
npx skills add . --list
npx skills add . --skill kttp
```

第一条命令只列出找到的 Skill，不执行安装。这两条命令可用于发布前检查本地修改；
GitHub 命令读取已发布版本。

## Skill 包含的内容

| 参考文件 | 主题 |
| --- | --- |
| `skills/kttp/SKILL.md` | 工作方式、语法边界以及执行方式选择 |
| `skills/kttp/references/syntax.md` | 请求构建器、响应、环境结构、文件和客户端设置 |
| `skills/kttp/references/recipes.md` | JSON、表单、multipart、委托登录和 Kotlin 复用 |
| `skills/kttp/references/execution.md` | IDEA/CLI 执行、GUI 模式和故障排查 |

安装 Skill 时会一起包含这些参考文件。在源代码仓库中，这些路径相对于仓库根目录。
线上文档还提供 [Kotlin 脚本介绍](scripting.md)和 [CLI 参考](cli.md)。

## 可以交给 Agent 的示例

- “写一个 Kttp POST 请求，使用 JSON 正文并检查状态码和返回 id。”
- “把这条 cURL 命令转换为 `.kttp.kts`，并使用现有环境配置。”
- “解释这个脚本为什么可以 Follow Script，但不能使用 Quick Request。”
- “不发送 HTTP 请求地编译这个脚本，并说明实际验证了什么。”

向 Agent 提供 API 契约、所需环境键和运行环境，并要求它区分生成的代码、编译过的脚本和实际执行过的脚本。
仅编译会检查语法和类型，不验证端点响应、环境值或运行时文件。
