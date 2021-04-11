import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
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

                implementation(libs.bundles.serialization)

                api(libs.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.coroutines.core)
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val jvmMain by getting
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit5"))

                implementation(libs.junit.jupiter)
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

