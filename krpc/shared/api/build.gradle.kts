import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version Versions.kotlin
}

kotlin {
    jvm()
    ios()
    tvos()
    macosX64()
    js()

    val serialization_version = "1.0.1"
    val ktor_version = "1.5.1"

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(File(buildDir, "generated/ksp/src/main/kotlin"))

            dependencies {
                implementation(kotlin("stdlib-common"))

                api(project(":krpc-annotations"))
                implementation("io.ktor:ktor-client-websockets:$ktor_version")

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$serialization_version")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_version")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$serialization_version")

                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2-native-mt") {
                    version {
                        strictly("1.4.2-native-mt")
                    }
                }
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit5"))

                implementation("org.junit.jupiter:junit-jupiter:5.6.2")
            }
        }
    }
}

tasks.withType<KotlinJvmTest> {
    useJUnitPlatform()
}

