plugins {
    id("hyperdrive-ide-plugin")
}

description = "IntelliJ IDEA plugin for Hyperdrive."

intellij {
    version.set("2023.2")
    type.set("IC")
}

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly(kotlin("stdlib-jdk8"))
    implementation(project(":plugin", configuration = "shadow")) {
        exclude(group = "org.jetbrains.kotlin")
    }
}

tasks.runPluginVerifier {
    ideVersions.set(
        listOf(
            "IU-232.8660.185",
        )
    )
}

tasks.buildPlugin {
    archiveAppendix.set("IC")
}

tasks.patchPluginXml {
    sinceBuild.set("232")
    version.set("${project.version}-IC")
}
