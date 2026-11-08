# 架构

Kttp 包含三个 Gradle 模块。独立 CLI 可运行 Kotlin 脚本；IDEA 插件提供编辑器、项目和 GUI 集成。

| 模块 | 职责 |
| --- | --- |
| `core/kttp-dsl` | 请求构建与注册、运行时上下文、OkHttp 执行、脚本定义、响应捕获、环境/存储和资源 |
| `core/kttp-cli` | CLI 参数、脚本编译与求值、请求选择和结构化执行结果 |
| `idea/kttp-idea` | 文件类型和编辑器、脚本 Run、请求提取、草稿存储、GUI 直接执行、响应与历史记录 |

OkHttp 执行逻辑属于 `kttp-dsl`；当前工程没有单独的 `kttp-executor` 模块。Core 模块不依赖 IntelliJ
PSI、编辑器或 Swing API。IDEA 插件依赖 DSL，并包含独立 CLI fat JAR。

## 脚本执行

```text
.kttp.kts 源文件
    → Kttp CLI / Kotlin 脚本宿主
    → 求值 Kotlin 并注册请求
    → 选择请求并解析委托依赖
    → RequestBuilder / 运行时上下文 / OkHttp
    → 响应捕获、文件保存、then / thenResponse
    → CLI 结果和 IDEA 展示
```

请求 holder 延迟构建和执行 HTTP 请求。回调可以检查响应或将其映射为值；委托值可能引入请求依赖。
请求选择只限制已注册请求的执行范围，不会跳过加载脚本时的顶层 Kotlin 求值。

编辑器 Run 和 GUI Follow Script 共用 CLI 定位、项目 JDK 选择和参数构建流程。
插件 ZIP 已包含 `cli/kttp-cli.jar`，用户不需要源代码仓库或单独构建 CLI。

## GUI 执行和源码归属

```text
脚本 PSI → 静态请求提取 → 只读的导入模型
                                 │
                      Follow Script ──→ 保存源文件 → 捆绑 CLI

新草稿 / Detach → 可编辑 RequestSpec → Quick Request → OkHttp
                         │
                         └→ 生成关联脚本 → Follow Script
```

静态提取从源码中获取 GUI 能表示的 HTTP 数据，不对任意 Kotlin 代码求值。
能力标签和文件过滤器描述提取能力，与选定的执行模式无关。Follow Script 也能运行无法直接映射为 GUI HTTP 模型的请求。

草稿修改作用于工作副本，保存、生成脚本和执行请求分别进行。导入请求以源脚本为准；
生成器通过文件指纹检查保留手动修改，包括编辑器中尚未保存的内容。
执行结束时，只有草稿身份和版本仍匹配，结果才会更新草稿元数据，避免覆盖用户切换后的其他请求。

## 响应和持久化

运行时捕获请求/响应元数据、保存响应文件，并向 UI 提供有大小限制的预览。响应文件保留完整正文，不受预览限制。
即使收到 HTTP 响应后断言失败，CLI 结构化结果和 IDEA 历史记录仍保留文件关联。

项目 GUI 草稿、生成脚本和 Quick 响应位于 `.kttp/` 下；编辑器 Run/Follow 响应和共享历史位于 IDE 配置目录的
`consoles/Kttp/responses/`。历史数据保存绝对路径，界面显示简短路径。详见 [GUI 存储和生命周期说明](gui.md)。

## 重要边界

- GUI Quick Request 发送可表示的 HTTP 模型，不会求值脚本回调或客户端配置。
- 完整脚本执行包含 JVM 启动和 Kotlin 编译开销；单独的 HTTP 耗时无法代表整个过程。
- 脚本环境、文件、委托存储和 Cookie 会话取决于运行器配置。
- Release 包和文档站从同一标签的源代码构建，并在 CI 检查成功后发布。
