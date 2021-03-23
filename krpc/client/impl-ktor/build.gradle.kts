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
                api(project(":krpc-client-impl"))
                api(project(":krpc-shared-impl-ktor"))

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.serialization}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.serialization}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:${Versions.serialization}")

                implementation("io.ktor:ktor-client-websockets:${Versions.ktor}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:${Versions.ktor}")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit5"))

                implementation("org.junit.jupiter:junit-jupiter:5.6.2")
            }
        }

        val iosMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:${Versions.ktor}")
            }
        }

        val jsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:${Versions.ktor}")
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

