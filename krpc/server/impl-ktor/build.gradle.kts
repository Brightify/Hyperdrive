import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

plugins {
    kotlin("multiplatform")
    `maven-publish`
}

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                api(project(":krpc-server-impl"))
                api(project(":krpc-shared-impl-ktor"))

                implementation("io.ktor:ktor-server-core:${Versions.ktor}")
                implementation("io.ktor:ktor-server-netty:${Versions.ktor}")
                implementation("io.ktor:ktor-websockets:${Versions.ktor}")

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.serialization}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.serialization}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:${Versions.serialization}")
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
