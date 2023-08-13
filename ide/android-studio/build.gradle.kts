plugins {
    id("hyperdrive-ide-plugin")
}

description = "Android Studio plugin for Hyperdrive."

intellij {
    version.set("2021.3.1.17")
    type.set("AI")
}

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly(kotlin("stdlib-jdk8"))
    implementation(project(":plugin", configuration = "shadow"))
}

tasks.runPluginVerifier {
    ideVersions.set(
        listOf(
            "IU-222.4345.14",
        )
    )
}

tasks.buildPlugin {
    archiveAppendix.set("AI")
}

tasks.patchPluginXml {
    sinceBuild.set("213")
    version.set("${project.version}-AI")
}
