import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij") version "0.6.5"
}

description = "IntelliJ IDEA plugin for Hyperdrive."

dependencies {
    implementation(project(":plugin-impl", configuration = "shadow"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

tasks.jar {
    manifest {
        attributes["Specification-Title"] = project.name
        attributes["Specification-Version"] = project.version
        attributes["Implementation-Title"] = "org.brightify.hyperdrive"
        attributes["Implementation-Version"] = project.version
    }
}

intellij {
    pluginName = "hyperdrive"
    version = "2020.3.2"
    setPlugins("gradle", "org.jetbrains.kotlin:203-1.4.21-release-IJ6682.9", "com.intellij.java")

    updateSinceUntilBuild = false
}

tasks.buildSearchableOptions {
    enabled = false
}