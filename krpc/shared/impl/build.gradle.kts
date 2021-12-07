import org.brightify.hyperdrive.configurePlatforms

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    // Include in documentation generation.
    id("org.jetbrains.dokka")
}

kotlin {
    configurePlatforms(appleSilicon = true)
    explicitApiWarning()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":krpc-annotations"))
                api(project(":krpc-shared-api"))
                implementation(project(":logging-api"))
                implementation(project(":kotlin-utils"))

                implementation(libs.coroutines.core)
                implementation(libs.stately.common)
                implementation(libs.stately.concurrency)
                implementation(libs.bundles.serialization)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(project(":krpc-test"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit5"))

                implementation(libs.coroutines.test)
                implementation(libs.junit.jupiter)
                implementation(libs.bundles.kotest.jvm)
            }
        }
    }

    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
                optIn("kotlinx.serialization.ExperimentalSerializationApi")
            }
        }
    }
}

tasks.dokkaHtmlPartial.configure {
    moduleName.set("Tachyon Shared Implementation (${moduleName.get()})")
}
