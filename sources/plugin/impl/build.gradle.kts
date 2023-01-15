plugins {
    id("hyperdrive-kotlin-plugin")
    alias(libs.plugins.shadow)
}

dependencies {
    api(project(":multiplatformx-plugin"))
    api(project(":krpc-plugin"))
}

tasks.shadowJar {
    relocate("org.jetbrains.kotlin.com.intellij", "com.intellij")
    mergeServiceFiles()
    archiveClassifier.set("shadow")
}
