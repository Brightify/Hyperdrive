import org.brightify.hyperdrive.configurePlatforms
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

plugins {
    id("build-setup")
    alias(libs.plugins.kotlin.plugin.serialization)
    // Include in documentation generation.
    alias(libs.plugins.dokka)
}

kotlin {
    configurePlatforms(appleSilicon = true)

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":krpc-annotations"))
                api(project(":krpc-client-api"))
                api(project(":krpc-shared-impl"))
                api(project(":logging-api"))

                implementation(libs.bundles.serialization)
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

                implementation(libs.junit.jupiter)
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>() {
    this.testLogging {

        showStandardStreams = true
    }
}

tasks.dokkaHtmlPartial.configure {
    moduleName.set("Tachyon Client Implementation (${moduleName.get()})")
}
