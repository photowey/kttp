# GUI 请求

打开 **View → Tool Windows → Kttp**。侧栏列出草稿、脚本和响应文件；主区域包含请求编辑器，
以及 **Response**、**History** 和 **Assertions** 视图。

新建 GUI 请求默认包含下面的可编辑示例，点击 **Send** 后才会发送：

```kotlin
GET("https://httpbin.org/get") {
    query("source", "kttp")
    header("Accept", "application/json")
}
```

**Sources** 的分组对应不同的存储范围：

- **Drafts**：已保存的 GUI 请求。打开脚本不会自动创建已保存草稿。
- **Project**：当前项目及其模块内容根下的 `.kttp.kts` 脚本，不限于 `.kttp/`；
  IDEA 排除的目录和版本控制忽略的文件不参与扫描。
- **Kttp**：当前运行的 IDE 配置目录 `consoles/Kttp/` 下的脚本。其他 IDEA 安装或开发沙箱可能使用不同的配置目录。

在项目中首次打开时，Kttp 会在 IDE 可用宽度内展开过窄的停靠工具窗口，并为导航区设置初始宽度。
之后手动调整的窗口大小和保存的分隔条比例优先。

## 导入 cURL 或 Postman

在脚本编辑器中选择 **+ → Import Requests…**，将所选请求插入当前脚本。光标位于顶层请求或声明内部时，
插入位置在该声明之后。如果导入代码复用的脚本属性声明位于后面，插入位置会移至这些声明之后，避免在初始化前使用属性。
已有代码保留；脚本修改通过 IDE 写命令完成，支持编辑器 Undo。
在 GUI **Sources** 区域点击 **Import Requests…**，则创建已保存的 GUI 草稿。
弹窗左侧为输入来源，右侧上方为请求列表、下方为脚本和诊断页签。底部的 **Destination** 和 **Environment** 分别与上方两栏共用左右分隔条，缩放和拖动时保持对齐；目标标签按 IDE 字体宽度分配空间，悬停目标摘要可查看完整路径。状态和 Import/Cancel 按钮位于设置下方。拖动分隔条可调整宽度和预览高度。
GUI 导入完成后，Sources 会清空搜索和过滤条件、展开 Drafts、选中第一条新请求，并显示本次导入数量。
两个入口共用同一窗口，沿用现有 IDE 主题控件。

1. 在 **Format** 中选择 **cURL** 或 **Postman**。输入字段支持 **Text** 与 **Paste**，或 UTF-8 **File**。
2. Postman 可选择 Collection 和可选的 Environment；留空 Collection 可仅导入 Environment，此操作不修改当前脚本。
   **Base directory** 默认使用当前工程解析相对上传路径；清空后，回退到所选源文件所在目录。
   点击 Browse 时优先定位到已有来源文件，其次使用所选基础目录或当前工程目录。
3. 点击 **Refresh Preview**，检查请求名称、方法、URL 和来源路径，选择要导入的请求，
   并阅读 **Generated Script**、**Diagnostics** 和 **Retained Source**。
   解析和预览在后台进行，**Cancel Preview** 可停止预览准备。
4. 确认显示的目标位置后，点击 **Import** 写入所选内容。**Cancel** 关闭窗口，不修改脚本、草稿或环境文件。
   输入、格式或目标变化会使预览失效，需要重新刷新。

导入不会执行 curl、shell、JavaScript 或 HTTP 请求。致命输入错误会阻止导入；请求错误会阻止选择对应条目。
带有不支持的脚本或需核对选项的请求默认不勾选；明确勾选仅表示接受其静态字段。
不支持的来源内容会保存为导入目标旁的 `.import-report.txt` 文件，供后续核对，不会变成可执行的 Kotlin 或断言。

### 支持的输入与限制

| 输入 | 导入内容 | 边界 |
| --- | --- | --- |
| cURL | 单个 HTTP(S) 请求；方法、URL/查询、重复请求头、静态 Cookie、Basic/Bearer 凭据、文本/JSON、URL 编码数据、multipart 和支持的文件引用 | 支持 POSIX 引号、转义和反斜杠续行；未知选项、shell 展开、多次传输和不支持的组合会被拒绝 |
| Postman Collection v2.0/v2.1 | 按原始顺序递归读取文件夹，保留名称/来源路径；字符串或结构化 URL、启用的查询/请求头/正文字段、raw 文本/JSON、urlencoded、form-data 和文件引用 | 按选择导入请求，省略禁用字段；不支持 Postman v1、Workspace 备份和云端同步 |
| Postman 认证 | 继承的 No Auth、静态 Basic/Bearer，以及位于请求头或查询中的 API key | 请求配置优先于最近文件夹、外层文件夹和 Collection；OAuth 流程、签名和其他认证类型需要人工转换 |
| Postman 脚本/变量 | 静态变量引用、带来源路径的诊断，以及供核对的不支持内容 | 不执行或翻译 pre-request/test JavaScript；动态变量和脚本生成的值需要人工处理 |

cURL 导入不解释任意 Bash、cmd 或 PowerShell 程序，只接受明确的选项集合，不覆盖所有 curl 参数。
部分响应显示/输出选项会省略并给出信息诊断；`--compressed` 需要核对，因为目标客户端的压缩行为可能不同。
文件正文支持 `--data-binary @path` 和 `--json @path`；`--data @path` 等文件内容转换、标准输入和 Cookie 文件会被拒绝。
URL 中的字面 `{` 或 `}` 即使使用 `--globoff` 也会被拒绝，因为 Kttp 将花括号视为路径参数。
需要字面花括号时，请使用 `%7B` 和 `%7D`。接受转换前请检查诊断。

cURL multipart 文件未显式设置 `;type=` 时，会保留支持的文件名到 MIME 类型映射。
未知扩展名回退为 `application/octet-stream`，并给出需要明确勾选请求的警告。
需要其他正文类型时请设置 `;type=`；导入不会通过读取文件内容推断类型。

上传文件保留为引用。导入只对字面相对路径做语法解析，不读取或检查上传内容；显式执行生成的请求时才读取文件。
粘贴命令中的相对路径使用界面显示的基础目录（默认为当前工程）；发送前请确认文件和运行时访问权限。
multipart 文件名默认取解析后上传路径的最后一段，包括运行时由环境变量提供的路径；显式导入的文件名优先。

Kttp/OkHttp 不支持 GET 和 HEAD 正文。Postman 导入中，这类正文及请求头名、查询参数名、
form/multipart 字段名或 API-key 名中的变量引用会产生请求级错误，对应条目无法勾选，其他有效请求仍可导入。
请在来源中将这些名称改为静态文本，或移除不支持的正文。支持字段值中的变量引用仍按下文处理。


**Import into** 沿用入口默认值：编辑器为 **Current Script**，侧栏为 **GUI Drafts**。
选择 **New Script** 可在 `<工程>/.kttp/imports/` 下创建一个保留请求分组和顺序的脚本；目录和基于 Collection 名称的文件名可修改。
同名时添加数字后缀，不覆盖已有文件；环境文件写在新脚本旁边。仅导入 Environment 时不创建空脚本。
混合变量值生成普通 Kotlin 字符串模板和共享的 `String by env` 声明；复用兼容声明，同名冲突需先处理。

**Use imported environment** 仅在尚未选环境且只创建一个配置时默认勾选。
否则保留已有选择，需主动启用；多个配置需明确选择。只有导入成功后才切换，使用 `postman_dev-2` 等实际创建的名称。
cURL 导入不会创建空环境配置。

### 变量与环境

Environment 可选。Collection 变量提供默认值，Environment 中启用的同名键覆盖默认值。
Folder 和 URL 变量保留请求范围。`{{name}}` 转换为 Kttp 值/环境引用；缺失键保留引用并给出诊断，
使用时按现有环境缺失规则报错，不会替换为空字符串。Postman 动态变量需要人工转换。

导入向 `kttp.env.json` 添加 `postman_<环境名>` 配置，同名时添加数字后缀，保留已有配置。
只有 Collection 默认值时使用 `postman_<集合名>`。
静态嵌套引用会在写入前解析；包含未定义值、动态变量或循环引用的配置值会省略并给出警告，
避免将未解析的 `{{tokens}}` 写成可执行默认值。请核对导入后创建的配置名和激活选项。

变量展开会缓存共享引用。单个展开值最多为 1,048,576 个 UTF-16 代码单元，缓存的引用文本和导入配置值的合计
分别最多为 8,388,608 个代码单元。每个解析器最多替换 10,000 次、嵌套 64 层。大小或替换次数超限时，
会在写入文件前拒绝导入；无法解析、循环或嵌套过深的环境值会省略。环境转换在弹窗后台线程执行，
目标环境文件若被并发修改，则需刷新预览。

导入提交和 GUI 脚本准备通过同步 VFS 操作完成本地写入，避免 IDEA 263 的文档保存仍在队列中时，
外部 CLI 读到旧文件。保留 IDEA 的保存处理、编码和换行格式，不使用实验性的刷新 API。

脚本导入将配置写在目标脚本旁；GUI 导入将环境和需要脚本的请求写入当前 IDE 的 `consoles/Kttp/` 目录。
仅导入环境时，**Choose Environment Folder…** 可选择目标目录。
现有环境查找规则仍适用：脚本目录的 `kttp.env.json` 优先于控制台文件，两处配置不会合并。

### GUI 草稿与关联脚本

编辑器可表示的请求成为可编辑的已保存草稿，支持 **Quick Request** 和 **Generate Script**。
自定义方法、multipart/文件正文及需要 Kotlin 的混合变量表达式会保留关联脚本，默认使用 **Follow Script**。
请求语义保留在脚本中；编辑器无法表示时，不能使用 Quick Request。
通过 **Open Script** 编辑关联脚本，再用 **Follow Script → Send** 执行。

生成的环境变量 URL 拼接在请求编辑器和脚本请求树中显示为 `${baseUrl}/path`。
该显示不执行脚本，变量值在运行时解析。其他动态 URL 显示 **Script URL (open source to edit)**。
这类请求仍为只读，并使用 Follow Script。

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
GUI 环境引用要求 `${baseUrl}` 占据整个字段，不支持 `${baseUrl}/users` 这样的字符串插值。
脚本使用普通 Kotlin 插值。JSON/Raw 正文字段也支持 `@file:path/to/body.json`。
仍接受旧的 `${env:name}` 输入，重新显示时统一为 `${name}`。在整个输入前加 `\` 可原样发送文本，
例如 `\${API_TOKEN}` 会发送 `${API_TOKEN}`，不解析环境值。已保存的字面量保留原有类型，重新打开时按需添加转义。
Sources 提示根据解析后的值标注来源，并识别字面量转义。

分离出的草稿在两种 GUI 执行模式下保留原脚本的环境文件查找位置；Follow Script 生成脚本时，
不会将环境查找切换到生成文件的目录。导入脚本仍使用自身的环境位置，没有文件关联的独立草稿使用 Consoles/Kttp。

脚本文件行显示请求总数，请求标签表示是否支持 Quick Request：

- **Quick available**（支持快速请求）：包括完整提取和仅提取 HTTP 数据的请求。Quick Request 仍可能省略脚本处理；
  悬停可查看编辑限制和省略的行为，需要这些行为时使用 Follow Script。
- **Script required**（需要脚本执行）：必须使用 Follow Script，仍可从 GUI 执行。

`request("CUSTOM", url)` 等自定义方法使用 Follow Script。编辑器无法表示该方法时，方法栏显示 **Script**；
该请求不能使用 Quick Request、Detach 或 GUI 脚本生成。

这些标签不代表当前执行模式。**Quick support** 过滤器按完整提取、仅 HTTP 数据、需要脚本执行和混合类别
筛选脚本文件，不会切换模式。搜索框按草稿标题或脚本文件名过滤。

## 响应和历史记录

**Response** 显示最近一次 GUI Send 的结果。脚本 Run 的文本输出位于 IDEA 的 **Run** 窗口，
保存的文件出现在 **Responses** 和 **History** 中。在 **Run** 中点击响应路径可在编辑器中打开文件，
右键菜单可在系统文件管理器中定位文件。

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
HTTP 文件导入仍为占位功能。
直接 GUI 模型不能保留的行为见 [Kotlin 脚本](scripting.md)。
