import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
    // Include in documentation generation.
    alias(libs.plugins.dokka)
}

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                api(project(":krpc-server-impl"))
                api(project(":krpc-shared-impl-ktor"))

                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.websockets)

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
    moduleName.set("Tachyon Server Implementation Ktor (${moduleName.get()})")
}
