import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    alias(libs.plugins.shadow)
    alias(libs.plugins.buildconfig)
}

dependencies {
    api(project(":multiplatformx-plugin"))
    api(project(":krpc-plugin"))

    compileOnly(kotlin("compiler-embeddable"))
    compileOnly(libs.auto.service)
    kapt(libs.auto.service)
}

tasks.shadowJar {
    relocate("org.jetbrains.kotlin.com.intellij", "com.intellij")
    mergeServiceFiles()
}

tasks.shadowJar {
    archiveClassifier.set("shadow")
}
