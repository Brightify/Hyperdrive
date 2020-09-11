import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "1.4.0"
    id("org.brightify.hyperdrive")
}

kotlin {
    /* Targets configuration omitted.
    *  To find out how to configure the targets, please follow the link:
    *  https://kotlinlang.org/docs/reference/building-mpp-with-gradle.html#setting-up-targets */
    jvm()
    ios()
    tvos()
    macosX64()
    js()

    val serialization_version = "1.0.0-RC"
    val ktor_version = "1.4.0"

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(File(buildDir, "generated/ksp/src/main/kotlin"))

            dependencies {
                implementation(kotlin("stdlib-common"))

                implementation(project(":krpc:krpc-annotations"))
                implementation("io.ktor:ktor-client-websockets:$ktor_version")

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$serialization_version")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$serialization_version")

                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9-native-mt") {
                    version {
                        strictly("1.3.9-native-mt")
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

dependencies {
    "ksp"(project(":krpc:krpc-processor"))

}

tasks.withType<KotlinJvmTest> {
    useJUnitPlatform()
}

