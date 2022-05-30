import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij)
}

val ideType: String by project
description = "IntelliJ IDEA plugin for Hyperdrive."

dependencies {
    implementation(project(":plugin-impl", configuration = "shadow"))
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
    version.set("2021.2")
    type.set(ideType)

    plugins.addAll(
        "gradle",
        "com.intellij.java",
        "org.jetbrains.kotlin",
    )

    updateSinceUntilBuild.set(false)
}

tasks.runPluginVerifier {
    ideVersions.set(
        listOf(
            "IU-211.7142.36",
        )
    )
}

tasks.buildPlugin {
    archiveAppendix.set(ideType)
}

tasks.publishPlugin {
    token.set(System.getenv("ORG_GRADLE_PROJECT_intellijPublishToken") ?: "")
    channels.set(listOf(System.getenv("ORG_GRADLE_PROJECT_intellijChannels") ?: "default"))
}

tasks.buildSearchableOptions {
    enabled = false
}

tasks.patchPluginXml {
    sinceBuild.set(
        when (ideType) {
            "AS" -> "203"
            else -> "211"
        }
    )
}
