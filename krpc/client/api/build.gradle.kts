import org.brightify.hyperdrive.configurePlatforms
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

plugins {
    kotlin("multiplatform")
    // Include in documentation generation.
    id("org.jetbrains.dokka")
}

kotlin {
    configurePlatforms(appleSilicon = true)

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":krpc-shared-api"))
                api(libs.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))

                api(libs.coroutines.core)
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
    moduleName.set("Tachyon Client API (${moduleName.get()})")
}
