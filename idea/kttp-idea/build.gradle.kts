import java.nio.file.Files

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.intellij.platform)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

// Inherit platform interface defaults without generating obsolete compatibility bridges.
kotlin {
    compilerOptions.jvmDefault.set(org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY)
}

repositories {
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":core:kttp-dsl"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)

    intellijPlatform {
        intellijIdea(libs.versions.intellij.idea.asProvider())
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Plugin.Java)
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("com.intellij.modules.platform")
    }
}

configurations.named("testRuntimeClasspath") {
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler-embeddable")
    // Use the Kotlin plugin's scripting API, just as the installed plugin does.
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-common")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-jvm")
}

val cliJar = project(":core:kttp-cli").tasks.named<Jar>("shadowJar").flatMap { it.archiveFile }
tasks.named<Test>("test") {
    dependsOn(":core:kttp-cli:shadowJar")
    inputs.file(cliJar).withPropertyName("kttpTestCliJar")
    doFirst {
        systemProperty("kttp.test.cliJar", cliJar.get().asFile.absolutePath)
    }
}

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        id = providers.gradleProperty("intellij.idea.plugin.id")
        version = project.version.toString().replace("-SNAPSHOT", "")

        ideaVersion {
            sinceBuild = libs.versions.intellij.idea.since.build.get()
            untilBuild = "263.*"
        }

        changeNotes = """
            <p>Kttp 1.2.0 adds cURL and Postman import for IntelliJ IDEA 2026.3 (build 263).</p>
            <ul>
              <li>Preview requests and choose the current script, saved GUI drafts or a new project-local script.</li>
              <li>Group new scripts under .kttp/imports with editable paths, readable Kotlin templates and shared environment bindings.</li>
              <li>Prefix new Postman profiles with postman_ and offer activation after successful import, preserving existing selections by default.</li>
              <li>Align Destination/Environment with the resizable source/review columns, fit target labels to the IDE font and use project-based file chooser defaults.</li>
              <li>Use &#36;{name} for GUI environment fields; accept legacy &#36;{env:name} and literal escapes.</li>
              <li>Keep detached drafts' source environment lookup across execution modes and avoid generated-file indexing on EDT.</li>
              <li>Keep script definitions available when IDEA workers have no thread context classloader.</li>
              <li>Reveal imported GUI drafts and display environment URL bindings without raw generated Kotlin.</li>
              <li>Parse supported POSIX cURL requests without executing curl, a shell or HTTP requests.</li>
              <li>Import Postman Collection v2.0/v2.1 with an optional Environment, or an Environment alone.</li>
              <li>Preserve missing variable references and create non-conflicting environment profiles.</li>
              <li>Resolve static nested environment values and report unresolved or cyclic entries.</li>
              <li>Bound variable expansion and prepare environment changes in the background before checking the target snapshot.</li>
              <li>Use resolved upload paths for default multipart filenames and preserve explicit overrides.</li>
              <li>Finish import and GUI script writes through synchronous VFS operations before completion or CLI execution.</li>
              <li>Keep custom methods, multipart/file bodies and unsupported GUI expressions in linked scripts.</li>
              <li>Retain Postman scripts and unsupported source for review without executing or translating JavaScript.</li>
              <li>Insert imported requests after reused script bindings and leave scripts unchanged for environment-only imports.</li>
              <li>Use IDEA's required document and PSI access when saving imports and restoring linked GUI drafts.</li>
              <li>Bundle the CLI and script libraries in each development sandbox as well as the installable ZIP.</li>
              <li>Update the bilingual guides and English Agent Skill with import behavior and limits.</li>
            </ul>
        """.trimIndent()
    }
}

tasks.named<Jar>("jar") {
    archiveFileName.set("kttp-idea.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// JAR include/exclude predicates (pattern-based, no hardcoded versions)
private val pluginLibPrefixes = listOf(
    "kttp-",
    "kotlin-script",
    "kotlin-reflect",
    "kotlin-stdlib",
    "kotlinx-",
    "maven-",
    "plexus-",
    "wagon-"
)

private val pluginLibSubstrings = listOf(
    "okhttp",
    "okio",
    "ok2curl",
    "jackson",
    "json-path",
    "guava",
    "guice",
    "slf4j",
    "javax.inject",
    "aopalliance",
    "commons-",
    "httpclient",
    "httpcore"
)

private val kttpExcludeSubstrings = listOf(
    "intellij", "idea", "platform",
    "jps-",
    "groovy",
    "junit-platform"
)

fun jarIncludePluginLib(name: String): Boolean {
    return pluginLibPrefixes.any { name.startsWith(it) }
        || pluginLibSubstrings.any { name.contains(it) }
}

fun jarIncludeKttpLib(name: String): Boolean {
    return jarIncludePluginLib(name)
        || name.startsWith("kotlin")
        || name.contains("kotlin-test")
        || name.contains("assertj")
        || name.contains("jcl-over")
}

fun jarExcludeKttpLib(name: String): Boolean {
    return kttpExcludeSubstrings.any { name.contains(it) }
}

fun jarSharedWithPlatform(name: String): Boolean {
    return name.startsWith("kotlin-script") || name.startsWith("kotlin-stdlib") ||
        name.startsWith("kotlin-reflect") || name.startsWith("kotlinx-coroutines")
}

// Each development/package sandbox owns its copies; runIde uses prepareSandbox_runIde.
// Test sandboxes retain the platform test classpath.
tasks.withType<org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask>().configureEach {
    if (name.startsWith("prepareSandbox")) {
        pluginJar.set(tasks.named<Jar>("jar").flatMap { it.archiveFile })
        // Share extension-point API types with the bundled Kotlin plugin. Script dependencies
        // remain separate in kttp-lib/; the CLI supplies its own compiler and runtime.
        runtimeClasspath.setFrom(configurations.runtimeClasspath.map { files ->
            files.filter { jarIncludePluginLib(it.name) && !jarSharedWithPlatform(it.name) }
        })

        from(configurations.runtimeClasspath) {
            include { jarIncludeKttpLib(it.name) }
            exclude { jarExcludeKttpLib(it.name) }
            into(pluginName.map { "$it/kttp-lib" })
        }
        from(cliJar) {
            rename { "kttp-cli.jar" }
            into(pluginName.map { "$it/cli" })
        }
    }
}

val prepareSandbox = tasks.named<org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask>("prepareSandbox")
val prepareRunSandbox = tasks.named<org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask>("prepareSandbox_runIde")

tasks.register("prepareKttpSandbox") {
    group = "intellij platform"
    description = "Prepares the default sandbox with the Kttp plugin and CLI"
    dependsOn(prepareSandbox)
}

tasks.register("verifyKttpSandboxes") {
    group = "verification"
    description = "Checks that package and runIde sandboxes contain identical Kttp payloads"
    val packaged = prepareSandbox.flatMap { it.pluginDirectory }
    val launched = prepareRunSandbox.flatMap { it.pluginDirectory }
    inputs.dir(packaged)
    inputs.dir(launched)
    inputs.file(cliJar)
    doLast {
        val packageDir = packaged.get().asFile
        val launchDir = launched.get().asFile
        val packageFiles = packageDir.walkTopDown().filter { it.isFile }
            .associateBy { it.relativeTo(packageDir).invariantSeparatorsPath }
        val launchFiles = launchDir.walkTopDown().filter { it.isFile }
            .associateBy { it.relativeTo(launchDir).invariantSeparatorsPath }
        check(packageFiles.keys == launchFiles.keys) { "Package and runIde sandbox file sets differ" }
        for ((path, file) in packageFiles) {
            check(Files.mismatch(file.toPath(), launchFiles.getValue(path).toPath()) == -1L) {
                "Package and runIde sandbox bytes differ: $path"
            }
        }
        check("lib/kttp-idea.jar" in packageFiles) { "Missing plugin JAR" }
        check(packageFiles.keys.any { it.startsWith("kttp-lib/kttp-dsl-") }) { "Missing script DSL JAR" }
        check(packageFiles.keys.none { it.startsWith("lib/") && jarSharedWithPlatform(it.substringAfterLast('/')) }) {
            "Plugin lib must share Kotlin scripting API types with the platform"
        }
        val copiedCli = packageFiles["cli/kttp-cli.jar"] ?: error("Missing bundled CLI")
        check(Files.mismatch(copiedCli.toPath(), cliJar.get().asFile.toPath()) == -1L) {
            "Bundled CLI differs from shadowJar"
        }
        logger.lifecycle("Verified {} identical Kttp files in package and runIde sandboxes", packageFiles.size)
    }
}

tasks.named<org.jetbrains.intellij.platform.gradle.tasks.BuildPluginTask>("buildPlugin") {
    archiveFileName.set("kttp-idea.zip")
}
