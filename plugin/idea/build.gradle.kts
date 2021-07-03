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
    pluginName.set("hyperdrive")
    version.set("2021.1.3")

    this.plugins.addAll(
        "gradle",
        "org.jetbrains.kotlin:211-1.5.10-release-909-IJ7142.45",
        "com.intellij.java",
    )

    updateSinceUntilBuild.set(false)
}

tasks.runPluginVerifier {
    ideVersions.set(
        listOf(
            "IU-211.7142"
        )
    )
}

tasks.publishPlugin {
    token.set(System.getenv("ORG_GRADLE_PROJECT_intellijPublishToken") ?: "")
    channels.set(listOf(System.getenv("ORG_GRADLE_PROJECT_intellijChannels") ?: "default"))
}

tasks.buildSearchableOptions {
    enabled = false
}
