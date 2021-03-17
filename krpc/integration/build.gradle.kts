import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version Versions.kotlin
    id("com.google.devtools.ksp") version Versions.symbolProcessing
}

kotlin {
    jvm()

    sourceSets {
        val jvmTest by getting {
            dependencies {
//                "ksp"(project(":krpc-plugin"))

                implementation(project(":krpc-annotations"))
                implementation(project(":krpc-server-api"))
                implementation(project(":krpc-shared-api"))
                implementation(project(":krpc-client-api"))

                implementation(project(":krpc-server-impl"))
                implementation(project(":krpc-shared-impl"))
                implementation(project(":krpc-client-impl"))

                implementation(kotlin("test"))
                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter:5.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutines}")

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.serialization}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.serialization}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:${Versions.serialization}")
            }

        }
    }
}

tasks.withType<KotlinJvmTest> {
    useJUnitPlatform()
}
