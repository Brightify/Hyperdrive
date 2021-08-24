plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    // Include in documentation generation.
    id("org.jetbrains.dokka")
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
                api(project(":krpc-shared-impl"))
                implementation(project(":logging-api"))
                implementation(project(":kotlin-utils"))

                implementation(libs.serialization.core)
                implementation(libs.ktor.client.websockets)
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
            languageSettings.useExperimentalAnnotation("kotlinx.serialization.ExperimentalSerializationApi")
        }
    }
}

tasks.dokkaHtmlPartial.configure {
    moduleName.set("Tachyon Shared Implementation Ktor (${moduleName.get()})")
}
