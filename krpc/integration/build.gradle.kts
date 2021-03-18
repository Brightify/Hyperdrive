import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    kotlin("jvm") apply false
    id("com.github.gmazzo.buildconfig") apply false
    id("com.github.johnrengelman.shadow")
}

publishing {
    publications.clear()
}

kotlin {
    jvm()

    sourceSets {
        val jvmTest by getting {

            dependencies {
                implementation(project(":krpc-annotations"))
                implementation(project(":krpc-server-api"))
                implementation(project(":krpc-shared-api"))
                implementation(project(":krpc-client-api"))

                implementation(project(":krpc-server-impl"))
                implementation(project(":krpc-shared-impl"))
                implementation(project(":krpc-client-impl"))
                implementation(project(":krpc-test"))
                implementation(project(":logging-api"))

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}") {
                    version {
                        strictly(Versions.coroutines)
                    }
                }

                implementation(kotlin("test"))
                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter:5.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutines}")
                implementation("io.kotest:kotest-runner-junit5:${Versions.kotest}") {
                    exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-core")
                }
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
    }
}

val krpcPlugin by configurations.creating

dependencies {
    krpcPlugin(project(":krpc-plugin"))
}

val krpcPluginJar by tasks.creating(ShadowJar::class) {
    configurations = listOf(krpcPlugin)
}

tasks.withType<KotlinCompile<*>> {
    dependsOn(krpcPluginJar)

    kotlinOptions {
        freeCompilerArgs += "-Xplugin=${krpcPluginJar.archiveFile.get().asFile.absolutePath}"

        println(freeCompilerArgs)
    }
}

tasks.withType<KotlinJvmCompile> {
    kotlinOptions {
        useIR = true
    }
}

tasks.withType<KotlinJvmTest> {
    useJUnitPlatform()
}

afterEvaluate {
    println(tasks.map { it.name })
}