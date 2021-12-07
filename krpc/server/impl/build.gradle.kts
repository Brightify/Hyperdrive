import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

plugins {
    kotlin("multiplatform")
    `maven-publish`
    // Include in documentation generation.
    id("org.jetbrains.dokka")
}

kotlin {
    explicitApi()

    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":krpc-server-api"))
                api(project(":krpc-shared-api"))
                api(project(":krpc-shared-impl"))
                api(project(":krpc-annotations"))

                implementation(project(":kotlin-utils"))
                implementation(project(":logging-api"))

                implementation(libs.bundles.serialization)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit5"))
                implementation(libs.junit.jupiter)
            }
        }
    }
}

tasks.dokkaHtmlPartial.configure {
    moduleName.set("Tachyon Server Implementation (${moduleName.get()})")
}
