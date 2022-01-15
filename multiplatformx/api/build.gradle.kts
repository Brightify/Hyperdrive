import org.brightify.hyperdrive.configurePlatforms

plugins {
    kotlin("multiplatform")
    id("io.kotest.multiplatform")
    // Include in documentation generation.
    id("org.jetbrains.dokka")
}

kotlin {
    configurePlatforms(appleSilicon = true)
    explicitApi()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":kotlin-utils"))
                api(project(":logging-api"))
                implementation(libs.stately.common)
                implementation(libs.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.coroutines.test)
                implementation(libs.bundles.kotest.common)
                implementation(kotlin("reflect"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.bundles.kotest.jvm)
                implementation(kotlin("test-junit5"))
                implementation(libs.junit.jupiter)
                implementation(libs.coroutines.test)
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}

tasks.dokkaHtmlPartial.configure {
    moduleName.set("Singularity API (${moduleName.get()})")
}
