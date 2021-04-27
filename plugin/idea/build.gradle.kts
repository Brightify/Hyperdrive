plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij")
}

description = "IntelliJ IDEA plugin for Hyperdrive."

dependencies {
    implementation(project(":plugin-impl-native"))
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

tasks.publishPlugin {
    token(System.getenv("ORG_GRADLE_PROJECT_intellijPublishToken") ?: "")
    channels(System.getenv("ORG_GRADLE_PROJECT_intellijChannels") ?: "default")
}

tasks.buildSearchableOptions {
    enabled = false
}
