plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlin.scripting.common)
    api(libs.kotlin.scripting.jvm)
    implementation(libs.kotlin.scripting.dependencies)
    implementation(libs.kotlin.scripting.dependencies.maven)
    api(libs.kotlin.reflect)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    api(libs.okhttp)

    api(libs.jackson.databind)
    api(libs.jackson.module.kotlin)

    api(libs.ok2curl)
    api(libs.json.path)

    // Assertion libraries - available in scripts (default imports)
    api(libs.kotlin.test)
    api(libs.assertj.core)

    testImplementation(libs.kotlin.test.junit5)
}

tasks.jar {
    from(sourceSets.main.get().allSource)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val versionResources = layout.buildDirectory.dir("generated/resources/kttpVersion")
val generateKttpVersion by tasks.registering(WriteProperties::class) {
    destinationFile.set(versionResources.map { it.file("kttp-version.properties") })
    property("version", project.version.toString())
}

sourceSets.main {
    resources.srcDir(versionResources)
}

tasks.processResources {
    dependsOn(generateKttpVersion)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("kttp.test.expectedVersion", project.version.toString().removeSuffix("-SNAPSHOT"))
}
