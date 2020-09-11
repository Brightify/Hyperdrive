import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

plugins {
    kotlin("multiplatform")
    id("org.brightify.hyperdrive")
}

group = "org.brightify.hyperdrive"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    ios()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(File(buildDir, "generated/ksp/src/main/kotlin"))

            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(project(":krpc:krpc-annotations"))
                implementation(project(":krpc:krpc-shared-api"))
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

