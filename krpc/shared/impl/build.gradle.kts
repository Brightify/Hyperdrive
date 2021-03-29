import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version Versions.kotlin
}

kotlin {
    jvm()
    ios()
    tvos()
    macosX64()
    js {
        browser()
        nodejs()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":krpc-annotations"))
                api(project(":krpc-shared-api"))
                implementation(project(":logging-api"))
                implementation(project(":kotlin-utils"))

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.serialization}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:${Versions.serialization}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.serialization}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(project(":krpc-test"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit5"))

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutines}")
                implementation("org.junit.jupiter:junit-jupiter:5.6.2")

                implementation("io.kotest:kotest-runner-junit5:${Versions.kotest}") {
                    exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-core")
                }
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}") {
                    exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-core")
                }
                implementation("io.kotest:kotest-property:${Versions.kotest}") {
                    exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-core")
                }
            }
        }
    }

    sourceSets {
        all {
            languageSettings.useExperimentalAnnotation("kotlinx.serialization.ExperimentalSerializationApi")
        }
    }
}

tasks.withType<KotlinJvmTest> {
    useJUnitPlatform()
}

