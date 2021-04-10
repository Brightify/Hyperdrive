import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.github.johnrengelman.shadow")
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project(":krpc-annotations"))
                implementation(project(":krpc-shared-api"))
                implementation(project(":krpc-client-api"))
                implementation(project(":krpc-client-impl-ktor"))
                implementation(project(":krpc-server-api"))
                implementation(project(":krpc-test"))

                implementation(project(":logging-api"))

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

                implementation(kotlin("test"))
                implementation("io.kotest:kotest-assertions-core")
                implementation("io.kotest:kotest-property")

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf")
            }
        }
        val jvmTest by getting {
            dependsOn(commonTest)

            dependencies {
                implementation(project(":krpc-server-impl-ktor"))

                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
                implementation("io.kotest:kotest-runner-junit5")
            }
        }
    }
}

val krpcPlugin by configurations.creating

dependencies {
    krpcPlugin(project(":krpc-plugin"))
}

val krpcPluginJar by tasks.creating(ShadowJar::class) {
    configurations = listOf(krpcPlugin)
    mergeServiceFiles()
}

val krpcNativePluginJar by tasks.creating(ShadowJar::class) {
    configurations = listOf(krpcPlugin)
    relocate("org.jetbrains.kotlin.com.intellij", "com.intellij")

    mergeServiceFiles()
}

tasks.withType<KotlinJvmCompile> {
    dependsOn(krpcPluginJar)

    kotlinOptions {
        useIR = true

        freeCompilerArgs += listOf(
            "-Xplugin=${krpcPluginJar.archiveFile.get().asFile.absolutePath}",
            "-P", "plugin:org.brightify.hyperdrive.krpc:enabled=true"
        )
    }
}

tasks.withType<KotlinNativeCompile> {
    dependsOn(krpcNativePluginJar)

    try {
        kotlinOptions {
            freeCompilerArgs += listOf(
                "-Xplugin=${krpcNativePluginJar.archiveFile.get().asFile.absolutePath}",
                "-P", "plugin:org.brightify.hyperdrive.krpc:enabled=true"
            )
        }
    } catch (t: Throwable) {
        // TODO: Fix why this is crashing on task `compileIosMainKotlinMetadata`
        t.printStackTrace()
    }
}

tasks.publish {
    enabled = false
}