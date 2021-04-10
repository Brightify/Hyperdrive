import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("com.github.johnrengelman.shadow")
    id("com.github.gmazzo.buildconfig")
}

dependencies {
    api(project(":multiplatformx-plugin"))
    api(project(":krpc-plugin"))

    compileOnly(kotlin("compiler-embeddable"))
    compileOnly("com.google.auto.service:auto-service")
    kapt("com.google.auto.service:auto-service")
}

tasks.shadowJar {
    relocate("org.jetbrains.kotlin.com.intellij", "com.intellij")

    mergeServiceFiles()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

