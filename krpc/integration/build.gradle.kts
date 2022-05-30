import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.shadow)
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.coroutines.core)
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

                implementation(libs.coroutines.core)

                implementation(kotlin("test-common"))
                implementation(libs.bundles.kotest.common)

                implementation(libs.bundles.serialization)
            }
        }
        val jvmTest by getting {
            dependsOn(commonTest)

            dependencies {
                implementation(project(":krpc-server-impl-ktor"))

                implementation(libs.junit.jupiter)
                implementation(libs.coroutines.test)
                implementation(libs.bundles.kotest.jvm)
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
