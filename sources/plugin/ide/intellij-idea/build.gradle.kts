plugins {
    id("hyperdrive-ide-plugin")
}

description = "IntelliJ IDEA plugin for Hyperdrive."

intellij {
    version.set("2022.2.3")
    type.set("IC")
}

tasks.runPluginVerifier {
    ideVersions.set(
        listOf(
            "IU-222.4345.14",
        )
    )
}

tasks.buildPlugin {
    archiveAppendix.set("IC")
}

tasks.patchPluginXml {
    sinceBuild.set("222")
    version.set("${project.version}-IC")
}
