---
hide:
  - toc
---

# 用 Kotlin 编写 HTTP 请求

**Kttp** 是基于 Kotlin 脚本的 HTTP 客户端，提供命令行运行器和 IntelliJ IDEA 插件。
请求可以写在 `.kttp.kts` 文件中，复用 Kotlin 代码并检查响应，也可以在 Kttp 工具窗口中编辑。

[快速开始](quick-start.md){ .md-button .md-button--primary }
[了解 GUI](gui.md){ .md-button }

```kotlin
GET("https://httpbin.org/get", name = "hello-kttp") {
    query("source", "kttp")
}.then {
    assertStatus(200)
    assertEquals("kttp", decode<String>("$.args.source"))
}
```

## 选择执行方式

| 入口 | 行为 | 适用场景 |
| --- | --- | --- |
| 脚本 Run / CLI | 对 Kotlin 求值，执行已注册的请求及其回调 | 脚本工作流、环境变量和可复用 Kotlin 代码 |
| GUI **Follow Script** | 通过共用的 CLI/JDK 启动流程，执行选中的脚本请求 | 需要 GUI 执行结果与源脚本行为保持一致 |
| GUI **Quick Request** | 直接发送界面能够表示的 HTTP 请求 | 不需要 Kotlin 求值或 `then` 的独立请求 |

Follow Script 保留脚本行为；Quick Request 跳过 JVM 启动和脚本编译。
切换模式前，请先了解[执行规则](gui.md)。

## 查找所需文档

- [快速开始](quick-start.md)：构建或安装插件，运行第一个请求。
- [Kotlin 脚本](scripting.md)：请求构建、断言、环境配置和本地文件。
- [GUI 请求](gui.md)：草稿、脚本关联、响应、历史记录和执行限制。
- [CLI 参考](cli.md)：在 IDE 之外编译、执行和选择请求。
- [Agent Skill](agent-skills.md)：向编码 Agent 提供 Kttp 的实际语法和工作方式。
- [架构](architecture.md)：DSL、CLI 与 IDEA 集成的职责边界。
- [开发与发布](development.md)：本地检查、文档构建，以及标签到 Release/Pages 的发布流程。

## 当前范围

插件以 IntelliJ IDEA 2026.3 为构建目标，设置 `sinceBuild=263.0`。开发使用 JDK 25，
脚本执行推荐使用 JDK 21 或 25。其他 IDE 产品和第三方主题尚未单独验证。
HTTP 文件和 Postman 导入操作目前是占位功能。

每次文档构建对应一个源代码版本。线上 Pages 站点跟随已发布的稳定 Release 部署；
推送到 `main` 只检查文档，不更新线上站点。
