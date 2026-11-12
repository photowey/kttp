plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    application
}

val cliMainClass = "io.github.photowey.kttp.cli.command.KttpCommandKt"

application {
    mainClass.set(cliMainClass)
}

dependencies {
    implementation(project(":core:kttp-dsl"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.clikt)

    // Kotlin Scripting
    implementation(libs.kotlin.scripting.common)
    implementation(libs.kotlin.scripting.jvm)
    implementation(libs.kotlin.scripting.dependencies)
    implementation(libs.kotlin.scripting.jvm.host)
    implementation(libs.kotlin.reflect)

    implementation(libs.slf4j.nop)

    testImplementation(libs.kotlin.test.junit5)
}

tasks.test {
    useJUnitPlatform()
}

// Shadow JAR configuration to handle service file merging
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    group = "build"
    description = "Creates a fat JAR with all dependencies"
    archiveClassifier.set("")

    manifest {
        attributes["Main-Class"] = cliMainClass
        attributes["Implementation-Version"] = project.version.toString().removeSuffix("-SNAPSHOT")
        attributes["Enable-Native-Access"] = "ALL-UNNAMED"
    }

    // Transformers must see every service/metadata file; ordinary duplicate entries stay excluded.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    mergeServiceFiles()
    filesMatching(listOf("META-INF/services/**", "META-INF/*.kotlin_module")) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    failOnDuplicateEntries = true

    // Exclude duplicate signatures
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

// Alias for backward compatibility
tasks.register("fatJar") {
    group = "build"
    description = "Builds the CLI fat JAR via shadowJar"
    dependsOn("shadowJar")
}

tasks.jar {
    archiveClassifier.set("plain")
    manifest {
        attributes["Main-Class"] = cliMainClass
    }
}
