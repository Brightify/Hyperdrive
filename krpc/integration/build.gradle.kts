import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    kotlin("jvm") apply false
    id("com.github.gmazzo.buildconfig") apply false
    id("com.github.johnrengelman.shadow")
}

kotlin {
    jvm()
    macosX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}") {
                    version {
                        strictly(Versions.coroutines)
                    }
                }
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project(":krpc-annotations"))
                implementation(project(":krpc-shared-api"))
                implementation(project(":krpc-client-api"))

                implementation(project(":krpc-client-impl-ktor"))
                implementation(project(":krpc-test"))
                implementation(project(":logging-api"))

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}") {
                    version {
                        strictly(Versions.coroutines)
                    }
                }

                implementation(kotlin("test"))
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}") {
                    exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-core")
                }
                implementation("io.kotest:kotest-property:${Versions.kotest}") {
                    exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-core")
                }

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.serialization}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.serialization}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:${Versions.serialization}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(project(":krpc-server-impl-ktor"))

                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter:5.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutines}") {
                    exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-core")
                }
                implementation("io.kotest:kotest-runner-junit5:${Versions.kotest}") {
                    exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-core")
                }
            }
        }
    }

    configurations.all {
        resolutionStrategy.eachDependency {
            val version = requested.version
            if (requested.group == "org.jetbrains.kotlinx" &&
                requested.name.startsWith("kotlinx-coroutines") &&
                version != null && !version.contains("native-mt")
            ) {
                useVersion(Versions.coroutines)
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

tasks.withType<KotlinJvmTest> {
    useJUnitPlatform()
}

tasks.publish {
    enabled = false
}