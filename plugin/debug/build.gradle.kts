import org.brightify.hyperdrive.configurePlatforms

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
    moduleName.set("Plugin Debug Annotations (${moduleName.get()})")
}
