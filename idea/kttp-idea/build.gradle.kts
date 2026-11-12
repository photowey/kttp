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
            <p>Kttp 1.1.0 targets IntelliJ IDEA 2026.3 (build 263).</p>
            <ul>
              <li>Target the IntelliJ Platform 263 SDK and Java 25.</li>
              <li>Load script templates through the Path-based Kotlin scripting API.</li>
              <li>Update the build toolchain and retain separate CLI runtime dependencies.</li>
              <li>Improve initial GUI sizing, project script discovery and new-request examples.</li>
              <li>Report the CLI version without requiring a script.</li>
              <li>Fix sidebar request import and refresh access to PSI.</li>
              <li>Export cURL without running the selected request's response callbacks.</li>
              <li>Run custom HTTP methods from the GUI through Follow Script.</li>
              <li>Support delegated request results without compatibility getters.</li>
              <li>Update the bilingual guides and Agent Skill for this version.</li>
            </ul>
        """.trimIndent()
    }
}

tasks.named<Jar>("jar") {
    archiveFileName.set("kttp-idea.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Follow the platform task's plugin destination, including custom sandbox and plugin names.
val prepareSandbox = tasks.named<org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask>("prepareSandbox")
val pluginDir = prepareSandbox.flatMap { it.pluginDirectory }

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

// Create lib/ with minimal jars (pattern-based; no version numbers to update on dependency bump)
tasks.register<Sync>("createPluginLib") {
    into(pluginDir.map { it.dir("lib") })

    from(tasks.named<Jar>("jar"))

    from(configurations.runtimeClasspath.get()) {
        include { jarIncludePluginLib(it.name) }
        // Share scripting API types with the Kotlin plugin across extension-point calls.
        // Independent script execution keeps its own versions in kttp-lib/ and the CLI.
        exclude {
            it.name.startsWith("kotlin-script") || it.name.startsWith("kotlin-stdlib") ||
                it.name.startsWith("kotlin-reflect") ||
                it.name.startsWith("kotlinx-coroutines")
        }
    }
}

// Collect script-definition dependencies from this module's runtime classpath into kttp-lib/.
// The separately bundled CLI fat JAR supplies the compiler and runtime used for script execution.
tasks.register<Sync>("createKttpLib") {
    into(pluginDir.map { it.dir("kttp-lib") })

    from(configurations.runtimeClasspath.get()) {
        include { jarIncludeKttpLib(it.name) }
        exclude { jarExcludeKttpLib(it.name) }
    }
}

// Copy kttp-cli fat jar to plugin directory (fat jar name is kttp-cli-<version>.jar, copy as kttp-cli.jar)
tasks.register<Sync>("copyKttpCli") {
    group = "intellij platform"
    description = "Copies kttp-cli fat jar to plugin directory"

    dependsOn(":core:kttp-cli:shadowJar")

    into(pluginDir.map { it.dir("cli") })

    from(cliJar) {
        rename { "kttp-cli.jar" }
    }
}

// Convenience task for assembling plugin/script libraries and the CLI; prepareSandbox remains registered.
tasks.register("prepareKttpSandbox") {
    group = "intellij platform"
    description = "Prepares the sandbox with kttp plugin"

    dependsOn(prepareSandbox, "createPluginLib", "createKttpLib", "copyKttpCli")
}

// Hook into prepareSandbox - run our tasks after it to override
tasks.named<org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask>("prepareSandbox") {
    finalizedBy("createPluginLib", "createKttpLib", "copyKttpCli")
}

// Keep the CLI copy after sandbox synchronization when runIde is launched from .run configurations.
tasks.named<Sync>("copyKttpCli") {
    mustRunAfter(prepareSandbox)
}

tasks.named("runIde") {
    dependsOn("copyKttpCli")
}

tasks.named<org.jetbrains.intellij.platform.gradle.tasks.BuildPluginTask>("buildPlugin") {
    dependsOn("createPluginLib", "createKttpLib", "copyKttpCli")
    archiveFileName.set("kttp-idea.zip")
}
