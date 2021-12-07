import org.brightify.hyperdrive.configurePlatforms

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    // Include in documentation generation.
    id("org.jetbrains.dokka")
}

kotlin {
    configurePlatforms()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":krpc-client-impl"))
                api(project(":krpc-shared-impl-ktor"))

                implementation(libs.bundles.serialization)

                implementation(libs.ktor.client.websockets)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit5"))

                implementation(libs.junit.jupiter)
            }
        }

        val iosMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
        }
    }
}

tasks.dokkaHtmlPartial.configure {
    moduleName.set("Tachyon Client Implementation Ktor (${moduleName.get()})")
}
