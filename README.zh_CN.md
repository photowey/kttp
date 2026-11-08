# `Kttp`

[English](README.md) | [简体中文](README.zh_CN.md) | [在线文档](https://photowey.github.io/kttp/zh/)

`Kttp` 是基于 Kotlin 脚本的 HTTP 客户端，提供命令行运行器和 IntelliJ IDEA 插件。
请求可以写在 `.kttp.kts` 文件中，也可以在 Kttp 工具窗口中编辑和发送。

## 文档

[中英文使用手册](docs/design/README.zh_CN.md)包含脚本、GUI 执行、CLI、架构和开发指南。
稳定版 GitHub Release 发布后，工作流会将对应版本的文档部署到 [Kttp Guide](https://photowey.github.io/kttp/)。
CI、Pages 设置和发布清单见[开发与发布](docs/design/docs/development.zh.md)。

## 模块

| 模块             | 职责                                                                    |
|------------------|-------------------------------------------------------------------------|
| `core/kttp-dsl`  | HTTP DSL、OkHttp 请求执行、脚本定义、响应捕获、环境变量、存储和文件资源 |
| `core/kttp-cli`  | Kotlin 脚本编译与求值，以及 CLI 入口                                    |
| `idea/kttp-idea` | IDEA 文件与编辑器集成、脚本运行配置、GUI 请求编辑器、响应与历史记录     |

## 本地构建与安装

插件以 IntelliJ IDEA **2025.3.2** 为构建目标，设置 `sinceBuild=253.0`，依赖 Kotlin 插件及
`plugin.xml` 中声明的平台语言和 JSON 模块。其他 IDE 产品尚未单独验证。
开发使用 JDK 21；执行脚本需要配置项目 JDK，推荐使用 JDK 21。

Gradle Wrapper 从官方 HTTPS 地址下载 Gradle 8.13，并校验 SHA-256。
首次构建需要联网下载 Gradle、依赖和 IntelliJ Platform，不需要配置开发者个人的 Maven 仓库或主目录。

依赖通过 Maven Central 和 Gradle Plugin Portal 解析；IntelliJ Platform 依赖使用平台插件的默认仓库。
无需额外指定仓库 Profile。本机 Gradle 设置请放在个人的 `~/.gradle/gradle.properties` 中。
开发测试实例使用 IntelliJ Platform 插件管理的 `idea/kttp-idea/build/idea-sandbox/` 沙箱，
不会重设 JVM 的 `user.home`。

请将仓库根目录作为一个 Gradle 工程导入。`core/` 和 `idea/` 只是根 `settings.gradle.kts`
中声明的模块分组，不是独立的构建工程。

构建插件：

```bash
./gradlew :idea:kttp-idea:buildPlugin
```

在 IDEA 中选择 **Settings → Plugins → Install Plugin from Disk…**，安装
`idea/kttp-idea/build/distributions/kttp-idea.zip`。该 ZIP 已包含 `cli/kttp-cli.jar`，
插件使用者无需单独构建 CLI。

## 编写和运行脚本

通过 **File → New → Kttp Script**，在 **Scratches and Consoles → Consoles → Kttp**
下创建脚本；也可以在项目中自行创建 `.kttp.kts` 文件。

```kotlin
GET("https://httpbin.org/get", name = "Get example") {
    query("source", "kttp")
}.then {
    assertStatus(200)
    val url: String = decode("$.url")
    println(url)
}

POST("https://httpbin.org/post", name = "Post JSON") {
    header("Content-Type", "application/json")
    body("""{"message":"Hello from Kttp"}""")
}.then {
    assertStatus(200)
    assertBodyContains("Hello from Kttp")
}
```

- 点击请求所在行左侧的 Run 图标，在脚本上下文中执行该请求。
- 编辑器右键菜单中的 **Execute Kttp Request**，默认快捷键为 `Ctrl+Alt+K`。
  除非调用上下文提供了请求位置，否则该操作运行整个文件。执行单个请求请使用行左侧的 Run 图标。
  实际快捷键取决于你的 Keymap。
- 使用 **Run All Requests**，按顺序执行文件中注册的请求。

脚本文本输出显示在 IDEA 的 **Run** 工具窗口。保存的响应文件也会出现在 Kttp 的 **Responses**
树和 **History** 中。Kttp 的 **Response** 标签页显示最近一次 GUI Send 的结果。

`then` 代码块的接收者是 `ThenContext`，可调用 `assertStatus`、`decode`、`getResponse` 等方法。
如果需要以 `Response` 为接收者，请使用 `thenResponse`。

## GUI 执行模式

打开 **View → Tool Windows → Kttp**，点击 **New Draft** 创建草稿，填写 URL，并选择执行模式：

| 模式                          | 执行方式                                                                                       | 适用场景                                         |
|-------------------------------|------------------------------------------------------------------------------------------------|--------------------------------------------------|
| **Follow Script（跟随脚本）** | 与脚本 Run 共用 CLI、JDK 选择和参数构建流程；对脚本求值，执行选定的请求，包括脚本配置和 `then` | 需要原脚本行为、动态 Kotlin 值或响应处理逻辑     |
| **Quick Request（快速请求）** | 通过 GUI 执行器直接发送界面中表示的 HTTP 请求，跳过 Kotlin 脚本求值、脚本级配置和 `then`       | 使用请求编辑器能够表示的数据，直接发送 HTTP 请求 |

新草稿默认使用 Quick Request，从脚本导入的请求默认使用 Follow Script；已保存的单请求执行模式会覆盖默认值。
如果 GUI 无法从脚本中静态提取可直接发送的 HTTP 请求，Quick Request 将不可用。
此时应使用 Follow Script，源脚本仍须能够成功编译和执行。

从脚本导入的请求在 GUI 请求编辑器中是只读的。你可以直接修改源脚本，或对支持提取的请求点击
**Detach**，创建可编辑草稿。切换执行模式不会让导入的请求变为可编辑。

Follow Script 会对源文件求值，包括顶层 Kotlin 代码，也可能执行通过委托关联的依赖请求。
启动 JVM 和编译脚本可能需要数秒，尤其是冷启动时；Quick Request 跳过这两个步骤。
**Total** 包含准备、HTTP 执行、正文下载、文件保存和结果解码；Follow Script 还包含 JVM 启动、
必要时的编译及脚本回调。**HTTP** 只记录最后一次 HTTP 交互从发送请求到收到响应头的耗时，
不包含脚本编译和正文下载。

发送期间显示转圈动画和已等待时间，点击 **Cancel** 可请求取消。GUI 可以先显示有大小限制的响应预览，
同时在后台继续保存完整响应和执行脚本。执行结束前，预览会标记为仍在运行；
结束后更新最终断言、耗时和历史记录。预览显示 HTTP 200 不代表脚本成功或文件已保存完成。
取得已保存文件的路径后，响应文件操作才会启用。

拖动分隔条，可以调整导航区、参数/脚本面板和响应区的大小。
请求编辑、响应预览和文件操作的详细说明见 [GUI 指南](docs/design/docs/gui.zh.md)。

## 草稿、脚本和响应文件

- **Save Draft**：保存 GUI 草稿。修改界面字段不会立即重写脚本。
- **Generate Script / Update Script**：根据 GUI 管理的草稿生成或更新脚本。如果已生成的文件被手动修改，
  包括编辑器中尚未保存的修改，则创建新文件以保留这些修改。生成脚本也会保存草稿及其文件关联。
- **Follow Script → Send**：对于 GUI 管理的草稿，先准备生成的脚本；对于从脚本导入的请求，
  保存并执行当前源文档，不用 GUI 生成的代码替换源脚本。
- **Detach**：将支持提取的脚本请求中的 HTTP 数据复制为可编辑草稿，并切换到 Quick Request。
  原脚本保持不变，新草稿不包含其 Kotlin 逻辑、客户端配置和回调。需要这些行为时，对原请求使用 Follow Script；
  需要保留新草稿时，点击 **Save Draft**。
- 脚本文件行显示请求总数。请求行统一显示 **Quick available**（支持快速请求）或 **Script required**
  （需要脚本执行）；悬停可查看编辑限制以及会被省略的脚本处理。这些标记表示支持能力，不代表当前执行模式。
  **Script required** 请求可以在 GUI 中使用 Follow Script 执行。**Quick support** 过滤器按提取能力筛选
  脚本文件，不会切换请求的执行模式。
- **History** 显示简短路径，但保留绝对路径供文件操作使用。双击条目可打开关联的响应文件。
  使用同一 IDE 配置目录的项目共享历史记录，目前没有 Re-run（重新执行）操作。

| 位置                                               | 用途                                                                                |
|----------------------------------------------------|-------------------------------------------------------------------------------------|
| `<project>/.kttp/requests/`                        | 根据 GUI 草稿生成的脚本                                                             |
| `<project>/kttp/temp/`                             | 未选择脚本时，由侧栏 Add Request 创建的脚本；需要本地项目目录                       |
| `<project>/.kttp/responses/`                       | 项目中的 Quick Request 响应文件                                                     |
| `<project>/.kttp/gui-state/drafts.json`            | 保存的 GUI 草稿                                                                     |
| `<IDE config>/consoles/Kttp/`                      | 控制台脚本；没有项目根目录时，也用于存放 GUI 生成的脚本                             |
| `<IDE config>/consoles/Kttp/gui-state/drafts.json` | 没有项目根目录时保存的 GUI 草稿                                                     |
| `<IDE config>/consoles/Kttp/responses/`            | 脚本 Run / Follow Script 响应、Quick 响应的后备目录，以及共享的 `kttp-history.json` |

`<IDE config>` 指当前运行的 IDE 的配置目录，具体位置由 IDE 产品、版本和设置决定。
响应预览最多读取 64 KiB 的解码后正文。GUI 在可选的 JSON 格式化前，取预览的前 50,000 个字符；
完整内容请打开保存的响应文件。

环境变量和本地文件的用法见[脚本语法参考](skills/kttp/references/syntax.md)；
IDEA/CLI 执行方式和 GUI 模式说明见[执行参考](skills/kttp/references/execution.md)。这些参考文档目前使用英文。

## Agent Skill

安装 `kttp` Skill，帮助编码 Agent 编写、解释和调试 `.kttp.kts` 脚本：

```bash
npx skills add photowey/kttp
# Select this skill explicitly:
npx skills add photowey/kttp --skill kttp
```

安装需要 Node.js 和 npm，支持的 Node.js 版本以 [Skills CLI](https://github.com/vercel-labs/skills) 的要求为准。
CLI 支持选择目标 Agent，默认安装到当前项目；使用 `-g` 可安装到用户级目录。
该 Skill 包含语法说明、完整请求示例和 IDEA/CLI 执行指南，不会安装 Kttp 插件、CLI 或 JDK。

以上命令从已发布的 GitHub 仓库读取内容。预览本地尚未发布的修改时，可在当前仓库根目录执行：

```bash
npx skills add . --list
npx skills add . --skill kttp
```

参见 [Skill 入口文档](skills/kttp/SKILL.md)。

## 开发命令

```bash
./gradlew build
./gradlew test
./gradlew :idea:kttp-idea:runIde
./gradlew :idea:kttp-idea:buildPlugin
./gradlew :core:kttp-cli:shadowJar
```

`runIde` 启动安装了当前插件的开发测试 IDEA 实例。`shadowJar` 在 `core/kttp-cli/build/libs/`
中生成包含依赖的独立 CLI JAR；名称以 `-plain.jar` 结尾的产物不包含依赖。

## 当前限制

- Quick Request 只发送 GUI 能够表示的 HTTP 数据，需要 Kotlin 脚本行为时请使用 Follow Script。
- Quick Request 的整体调用超时为 60 秒，不维护持久化 Cookie 存储。
- HTTP 文件和 Postman 导入操作目前是占位功能。
- `http2()` / **HTTP/2** 复选框选择的是 OkHttp 的 `H2_PRIOR_KNOWLEDGE` 模式，并非自动协商 HTTP/2 的开关。
- 尚未逐一验证所有 IDE 产品和第三方主题的兼容性。

## 致谢

感谢 [Connekt](https://github.com/Amplicode/connekt) 和 [Amplicode](https://amplicode.ru/) 背后的团队。
他们的设计思路和实现方式，为 Kttp 的 Kotlin HTTP 脚本能力和 IntelliJ IDEA 集成提供了启发与参考。
