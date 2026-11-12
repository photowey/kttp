# 快速开始

## 安装 IDEA 插件

开发请使用 JDK 25。插件以 IntelliJ IDEA 2026.3 为目标，依赖 Kotlin 插件，以及 `plugin.xml`
中声明的平台语言和 JSON 模块。Kttp 1.1.0 声明兼容构建号 `263.0` 至 `263.*`；
较早的 IDE 请使用 Kttp 1.0.1 等兼容旧版。

如有已发布的 [GitHub Release](https://github.com/photowey/kttp/releases)，下载其中的
`kttp-idea-X.Y.Z.zip`。在 IDEA 中打开 **Settings → Plugins → Install Plugin from Disk…**，
选择 ZIP，并按提示重启。该 ZIP 已包含执行脚本所需的 CLI。

也可以在源代码仓库根目录自行构建插件：

```bash
./gradlew :idea:kttp-idea:buildPlugin
```

在插件安装对话框中选择 `idea/kttp-idea/build/distributions/kttp-idea.zip`。
首次构建需要联网下载 Gradle、依赖和 IntelliJ Platform。请使用仓库内的 Gradle Wrapper，
不需要开发者个人的 Maven 仓库或主目录配置。

## 运行第一个脚本

1. 配置项目 JDK，推荐使用 JDK 21 或 25。
2. 选择 **File → New → Kttp Script**。新文件创建在
   **Scratches and Consoles → Consoles → Kttp** 下，也可以在项目中创建 `.kttp.kts` 文件。
3. 将文件内容替换为以下脚本：

```kotlin
GET("https://httpbin.org/get", name = "first-request") {
    query("source", "kttp")
}.then {
    assertStatus(200)
    val source: String = decode("$.args.source")
    println(source)
}
```

该示例需要访问 `httpbin.org`。测试自己的服务时，请替换地址和预期响应。

点击请求所在行左侧的 Run 图标。脚本输出位于 **Run** 工具窗口；Kttp 的 **Responses** 树和 **History**
中可以查看已保存的响应文件。**Run All Requests** 按顺序执行文件中注册的请求。
编辑器的 **Execute Kttp Request** 操作默认运行整个文件，除非调用上下文提供了请求选择信息；
需要执行单个请求时，请使用行左侧的 Run 图标。

## 从 GUI 发送请求

打开 **View → Tool Windows → Kttp**，点击 **New Draft**。草稿已包含 `GET https://httpbin.org/get`、
`source=kttp` 查询参数和 `Accept: application/json` 请求头。检查或修改示例，保持 **Quick Request** 模式，
点击 **Send**。打开草稿不会自动发送或保存。

使用 **Save Draft** 保存请求。**Generate Script** 会写入关联的 `.kttp.kts` 文件并保存草稿。
需要在脚本上下文中执行时，切换到 **Follow Script**。从已有脚本选择的请求默认使用 Follow Script，
在 GUI 请求编辑器中只读；需要修改时，编辑源脚本或使用 **Detach**。

各按钮的行为和两种模式的区别见 [GUI 请求](gui.md)。

## 使用独立 CLI

Release 中的 `kttp-cli-X.Y.Z.jar` 是包含依赖的 JAR。也可以从源代码构建：

```bash
./gradlew :core:kttp-cli:shadowJar
```

将 `KTTP_CLI_JAR` 设置为实际 JAR 路径，并将上面的示例保存为 `first.kttp.kts`：

```bash
java -jar "$KTTP_CLI_JAR" --script first.kttp.kts --json-output
```

名称以 `-plain.jar` 结尾的构建产物不包含依赖。响应目录、请求选择和仅编译检查见 [CLI 参考](cli.md)。
