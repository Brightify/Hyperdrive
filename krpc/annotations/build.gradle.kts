import org.brightify.hyperdrive.configurePlatforms

plugins {
    id("build-setup")
    alias(libs.plugins.kotlin.plugin.serialization)
    // Include in documentation generation.
    alias(libs.plugins.dokka)
}

kotlin {
    configurePlatforms(appleSilicon = true)
    explicitApi()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.serialization.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
    }
}

tasks.dokkaHtmlPartial.configure {
    moduleName.set("Tachyon Annotations (${moduleName.get()})")
}
