import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    ios()
    tvos()
    macosX64()
    js()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(File(buildDir, "generated/ksp/src/main/kotlin"))

            dependencies {
                implementation(kotlin("stdlib-common"))
                api(project(":krpc-annotations"))
                api(project(":krpc-shared-api"))
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

