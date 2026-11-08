# CLI 参考

独立 CLI 可在 IDEA 之外编译并求值 `.kttp.kts` 脚本。请使用兼容的 JDK，推荐使用 JDK 21。
从 Release 下载包含依赖的 JAR，或运行 `./gradlew :core:kttp-cli:shadowJar` 自行构建。
名称以 `-plain.jar` 结尾的产物不包含依赖。

将 `KTTP_CLI_JAR` 设置为实际 fat JAR，将 `KTTP_WORK_DIR` 设置为包含 `requests.kttp.kts` 和 `env.json`
的目录。这两个名称是下面示例使用的 Shell 变量，不是 Kttp 配置键。`env.json` 使用扁平字符串映射，
示例见 [Kotlin 脚本](scripting.md)。

## 执行请求

```bash
java -jar "$KTTP_CLI_JAR" \
  --script "$KTTP_WORK_DIR/requests.kttp.kts" \
  --env-file "$KTTP_WORK_DIR/env.json" \
  --base-dir "$KTTP_WORK_DIR" \
  --response-dir "$KTTP_WORK_DIR/responses" \
  --json-output
```

未指定选择器时，脚本求值后按顺序执行已注册的请求。添加 `--request-name read-user` 按名称选择，
或使用 `--request-number 0` 选择第一个请求。建议每次只使用一个选择器，并设置唯一名称。
选择请求不会跳过顶层 Kotlin 求值；读取委托结果也可能执行依赖请求。

## 仅编译，不求值

```bash
java -jar "$KTTP_CLI_JAR" \
  --script "$KTTP_WORK_DIR/requests.kttp.kts" \
  --execution-mode COMPILE_ONLY \
  --no-compilation-cache --json-output
```

仅编译会检查语法和类型，不会对脚本正文求值或发送其中注册的请求；它也不验证运行时环境值、文件是否存在、
端点行为或响应断言。`@file:DependsOn` 等依赖声明在编译时仍可能解析或下载依赖。

## 参数

| 参数 | 含义 |
| --- | --- |
| `--script <file>` | 必填脚本文件 |
| `--request-name <name>` | 按显式请求名或委托属性坐标选择 |
| `--request-number <n>` | 从零开始的注册索引，不同于 IDE 显示序号 |
| `--env-file <json>` | 扁平 `Map<String, String>`，不是 IDEA 命名配置 |
| `--env-param key=value` | 可重复指定，覆盖环境文件条目 |
| `--base-dir <directory>` / `-B` | `localFile` 相对路径基目录；默认推断脚本目录 |
| `--response-dir <directory>` | 响应输出目录；默认位于系统临时目录下的 `.kttp-responses` |
| `--storage-dir <directory>` | 持久化值目录；省略时使用单进程内存存储 |
| `--cookies-file <file>` | 持久化 Cookie 文件；省略时不维护会话 Cookie |
| `--execution-mode COMPILE_ONLY` | 编译但不对脚本正文求值 |
| `--execution-mode CURL` | 根据脚本请求生成 cURL，不通过 HTTP 发送；仍会执行 Kotlin 求值 |
| `--no-compilation-cache` | 强制重新编译，不复用编译缓存 |
| `--json-output` / `-j` | 输出最终结构化结果 |
| `--debug-log` / `-d` | 启用调试日志 |
| `--jvm-target <version>` | 脚本编译目标，默认 `1.8` |

IDEA 集成还会传入 `--request-lines first:last`、响应清单和快照参数。
源码行选择优先于有效的数字选择器，数字选择器优先于名称。常规 CLI 调用建议使用上表中的公开名称或序号参数。
CLI 没有用于选择 IDEA 命名环境的 `--env` 开关。

## 结果和错误

同时检查进程退出码和最终结果的 `success`/`error`。编译错误、回调失败和断言失败都会导致执行失败。
仅收到 HTTP 4xx/5xx 并不会自动使 CLI 失败；请在脚本中断言期望的状态码。

`--json-output` 不保证标准输出中只有一个 JSON 文档。脚本输出和编译消息可能位于最终 JSON 结果行之前，
诊断失败时应保留这些行。

持久化存储可能按属性名复用委托结果；测试全新登录时请省略 `--storage-dir`。
IDEA 脚本 Run 和 GUI Follow Script 当前不会通过共用参数流程传递持久化存储或 Cookie 文件设置。
Quick Request 有独立的[执行限制](gui.md)。
