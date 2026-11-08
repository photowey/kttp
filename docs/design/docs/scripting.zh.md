# Kotlin 脚本

Kttp 脚本使用 `.kttp.kts` 扩展名和 Kttp 脚本运行时。普通 `.kt` 文件不会自动获得脚本的构建器实例或默认导入。
请求声明用于注册待执行请求，不会立即返回 HTTP 响应。

## 构建请求

`GET`、`POST`、`PUT`、`PATCH`、`DELETE`、`HEAD`、`OPTIONS` 和 `TRACE` 接受 URL、可选 `name`
和构建器代码块。自定义方法使用 `request(method, path, name)`。下面是一个完整脚本：

```kotlin
val baseUrl: String by env

GET("$baseUrl/users/{id}", name = "read-user") {
    path("id", 42)
    query("tag", "kotlin")
    query("tag", "http client")
    header("Accept", "application/json")
}.then {
    assertStatus(200)
    assertEquals(42, decode<Int>("$.id"))
}
```

重复调用 `query` 会追加参数值，由 OkHttp 编码。`path` 在解析 URL 前替换占位符；
请使用合适的标识符，或自行编码任意路径段。通过 CLI 名称选择请求时，应保持请求名称唯一。

## 正文与认证

每个请求只能设置一种正文：同一构建器不能混用 `body`、`formData` 和 `multipart`。

```kotlin
val baseUrl: String by env
val apiToken: String by env

POST("$baseUrl/documents", name = "create-document") {
    bearerAuth(apiToken)
    contentType("application/json")
    body("""{"title":"Kttp example","enabled":true}""")
}.then {
    assertStatus(201)
}
```

Basic 认证使用 `basicAuth(username, password)`；API Key 使用实际接口要求的 Header 或查询参数。
`formData { field("name", value) }` 会执行表单编码。`multipart` 支持文本和文件 part，当前实现在内存中缓冲上传内容。
没有 `json { ... }` 正文构建器。动态 JSON 应通过 JSON 库序列化，不要直接插入未转义的文本。

## 检查和读取响应

`then` 的接收者是 **ThenContext**，提供 `statusCode`、`headers`、缓存的 `body`、`assertStatus`、
`assertHeader`、`assertBodyContains`，以及带类型的 JSONPath 提取方法 `decode<T>(path)`。

将相关断言和提取放在同一个 `then` 中。首次读取正文会消费底层流并缓存文本，
因此应避免同时直接读取 `getResponse().body`。对已映射的 holder 再次调用 `then` 时，
使用的是要求已有响应的立即执行重载，不会追加延迟回调。

需要以 OkHttp `Response` 为接收者时，使用 `thenResponse { ... }`，或在 `then` 中通过 `getResponse()` 获取。

## 环境配置

声明 `val baseUrl: String by env`，属性名就是键名。支持的标量类型包括 `String`、`Int`、`Long`、
`Boolean` 和 `Double`。这里使用属性委托，不是 `env()`，也不会自动读取操作系统环境变量。

IDEA 从脚本所在目录的 `kttp.env.json` 读取**命名配置**，只有文件不存在时才使用
`<IDE config>/consoles/Kttp/` 中的配置。本地文件无效或缺少所选配置时，不会再次查找，也不会合并两处配置。
在编辑器中选择配置名称：

```json
{
  "dev": {
    "baseUrl": "http://127.0.0.1:8080",
    "apiToken": "replace-with-your-test-token"
  }
}
```

CLI 的 `--env-file` 接受**扁平字符串映射**，没有外层配置名：

```json
{
  "baseUrl": "http://127.0.0.1:8080",
  "apiToken": "replace-with-your-test-token"
}
```

CLI 的 `--env-param key=value` 会覆盖文件中的值，没有用于选择 IDEA 配置名的 `--env` 参数。

## 本地文件和客户端设置

使用 `val payload: String by localFile("request.json")` 和 `body(payload)` 加载 UTF-8 文本正文。
相对路径以运行器提供的基目录为准：IDEA 优先使用项目根目录；CLI 优先使用 `--base-dir`，
未指定时使用推断的脚本目录。绝对路径直接读取。`localFile` 不是二进制上传辅助函数。

顶层 `configureClient { ... }` 配置 OkHttp；请求内的 `configure { ... }` 修改该请求的客户端。
`noRedirect()` 和 `noCookies()` 用于设置请求的重定向与 Cookie 行为。`http2()` 选择 `H2_PRIOR_KNOWLEDGE`，
不是自动协商 HTTP/2。[Follow Script](gui.md) 保留脚本配置，Quick Request 会跳过这些配置。
