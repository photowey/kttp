pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "kttp"

include(
    "core:kttp-dsl",
    "core:kttp-cli",
    "idea:kttp-idea"
)
