import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

val coreJvmTarget = JvmTarget.fromTarget(providers.gradleProperty("java.version").getOrElse("1.8"))

subprojects {
    group = property("project.group") as String
    version = property("project.version") as String
    val moduleJvmTarget = if (name == "kttp-idea") JvmTarget.JVM_25 else coreJvmTarget
    val javaTarget = moduleJvmTarget.target

    repositories {
        mavenCentral()
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(moduleJvmTarget)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(javaTarget.removePrefix("1.").toInt())
        sourceCompatibility = javaTarget
        targetCompatibility = javaTarget
    }

    tasks.withType<Jar>().configureEach {
        archiveBaseName.set(project.name)
        archiveVersion.set(project.version.toString().replace("-SNAPSHOT", ""))
    }
}
