# GUI 请求

打开 **View → Tool Windows → Kttp**。侧栏列出草稿、脚本和响应文件；主区域包含请求编辑器，
以及 **Response**、**History** 和 **Assertions** 视图。

## 执行模式

| 模式 | 执行内容 | 默认适用对象 |
| --- | --- | --- |
| **Follow Script** | 通过与脚本 Run 相同的 CLI/JDK 启动流程，执行源脚本中选定的请求 | 从脚本导入的请求 |
| **Quick Request** | 通过 GUI 执行器，直接发送请求编辑器能够表示的 HTTP 数据 | 新草稿 |

Follow Script 对源文件求值，执行选定请求及其依赖，并运行 `then` 等回调。求值时也会执行顶层 Kotlin 代码。
动态表达式、认证工作流、客户端配置和响应处理应使用此模式，源文件须能够成功编译和求值。
Follow Script 没有 GUI 整体执行超时，HTTP 超时仍由脚本的客户端设置控制；点击 Cancel 可终止正在运行的脚本进程。

Quick Request 跳过 Kotlin 求值、脚本级配置和 `then`，只支持 GUI 模型能够表示的请求。
整体调用超时为 60 秒，不维护持久化 Cookie 存储。已保存的单请求执行模式可以覆盖初始默认值。

## 编辑、保存和关联脚本

从脚本导入的请求在 GUI 请求编辑器中始终只读。需要修改时，直接编辑源脚本，
或对支持提取的请求点击 **Detach**，创建可编辑草稿。

| 操作 | 作用 |
| --- | --- |
| **New Draft** | 在内存中创建可编辑请求，使用 Quick Request 模式 |
| **Save Draft** | 持久化工作草稿，不立即重写脚本 |
| **Generate Script / Update Script** | 写入生成的 Kotlin，保存草稿并记录文件关联 |
| **Open Script** | 打开关联的源文件或生成文件 |
| **Detach** | 将提取的 HTTP 数据复制为可编辑草稿，切换到 Quick Request，原脚本保持不变 |
| **Send / Cancel** | 按选定模式执行，或取消正在运行的 HTTP 调用/脚本进程 |

生成文件如有手动修改，包括编辑器中尚未保存的内容，再次生成时会写入新文件，保留已有修改。
以 Follow Script 模式发送 GUI 草稿前，会先准备生成的脚本；发送导入请求时，则保存并执行当前源文档，
不会用生成代码替换它。

Detach 只复制提取出的 HTTP 模型。需要原始 Kotlin 逻辑和回调时，对源请求使用 Follow Script。
如需保留分离出的草稿，点击 Save Draft。

## 字段和侧栏过滤

通过 **Params**、**Headers**、**Body** 和 **Auth** 编辑支持的请求字段。
GUI 环境引用要求 `${env:baseUrl}` 占据整个字段，不支持 `${env:baseUrl}/users` 这样的字符串插值。
脚本使用普通 Kotlin 插值。JSON/Raw 正文字段也支持 `@file:path/to/body.json`。

脚本文件行显示请求总数，请求标签表示是否支持 Quick Request：

- **Quick available**（支持快速请求）：包括完整提取和仅提取 HTTP 数据的请求。Quick Request 仍可能省略脚本处理；
  悬停可查看编辑限制和省略的行为，需要这些行为时使用 Follow Script。
- **Script required**（需要脚本执行）：必须使用 Follow Script，仍可从 GUI 执行。

这些标签不代表当前执行模式。**Quick support** 过滤器按完整提取、仅 HTTP 数据、需要脚本执行和混合类别
筛选脚本文件，不会切换模式。搜索框按草稿标题或脚本文件名过滤。

## 响应和历史记录

**Response** 显示最近一次 GUI Send 的结果。脚本 Run 的文本输出位于 IDEA 的 **Run** 窗口，
保存的文件出现在 **Responses** 和 **History** 中。

发送期间持续显示转圈动画和已等待时间，直到执行或取消完成。界面可以先显示响应预览，
同时在后台继续保存完整响应文件和执行脚本回调。执行结束前，预览标记为仍在运行；
结束后更新最终断言、耗时和历史记录。预览显示 HTTP 200 不代表最终执行成功或文件已保存完成，
之后仍可能被取消，或因断言失败、保存失败而结束。

预览最多读取 64 KiB 的解码后正文，读取到这个大小或正文结束后才会回显。仅收到响应头不会触发预览，
也不会持续流式追加正文。GUI 在可选的 JSON 格式化前取前 50,000 个字符，完整正文请打开保存的文件。
如果脚本的实时预览监听器不可用，GUI 仍会显示最终执行结果。

响应区顶部集中显示状态码、耗时和文件操作。取得已保存文件的路径后，文件操作才会启用。
文件刷新和文件管理器启动在后台执行，Copy Path 使用 IDE 剪贴板。
拖动分隔条，可以调整导航区宽度、参数/脚本面板宽度，以及编辑器与结果区域的高度。

使用同一个 IDE 配置目录的项目共享历史记录。条目显示简短路径，实际文件操作保留绝对路径。
双击条目可打开响应文件，History 目前没有 Re-run 操作。

| 位置 | 内容 |
| --- | --- |
| `<project>/.kttp/requests/` | GUI 生成的脚本 |
| `<project>/kttp/temp/` | 未选择脚本时，由侧栏 Add Request 创建的脚本；需要本地项目目录 |
| `<project>/.kttp/gui-state/drafts.json` | 保存的草稿 |
| `<project>/.kttp/responses/` | 项目中的 Quick Request 响应 |
| `<IDE config>/consoles/Kttp/responses/` | 脚本 Run/Follow 响应、Quick 响应后备目录和 `kttp-history.json` |

没有项目根目录时，生成脚本和草稿状态也回退到 IDE 的 `consoles/Kttp/` 目录。
配置路径由当前运行的 IDE 决定。

## 理解耗时

**Total** 包含准备、HTTP 执行、正文下载、文件保存和结果解码。Follow Script 还包含 JVM 启动、
必要时的编译及脚本回调。**HTTP** 只记录最后一次 HTTP 交互到收到响应头的耗时，不包括脚本编译和正文下载。
因此，即使 HTTP 耗时很短，Follow Script 的启动和编译仍可能需要数秒。
Quick Request 跳过这些阶段。

**HTTP/2** 复选框选择 `H2_PRIOR_KNOWLEDGE`，不是自动协商 HTTP/2。
HTTP 文件和 Postman 导入操作仍是占位功能。直接 GUI 模型不能保留的行为见 [Kotlin 脚本](scripting.md)。
