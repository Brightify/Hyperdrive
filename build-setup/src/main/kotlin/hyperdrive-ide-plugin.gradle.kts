import org.gradle.kotlin.dsl.provideDelegate

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij")
}

dependencies {
    implementation(project(":plugin-impl", configuration = "shadow"))
}

sourceSets.main {
    kotlin.srcDir("../common/src/main/kotlin")
    resources.srcDir("../common/src/main/resources")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
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
    pluginName.set("hyperdrive")

    plugins.addAll(
        "gradle",
        "com.intellij.java",
        "org.jetbrains.kotlin",
    )

    updateSinceUntilBuild.set(false)
}

tasks.publishPlugin {
    val intellijPublishToken: String? by project
    val intellijChannels: String? by project
    token.set(intellijPublishToken)
    channels.set(listOf(intellijChannels ?: "default"))
}

tasks.buildSearchableOptions {
    enabled = false
}
