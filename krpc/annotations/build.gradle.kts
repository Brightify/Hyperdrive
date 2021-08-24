plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    // Include in documentation generation.
    id("org.jetbrains.dokka")
}

kotlin {
    /* Targets configuration omitted. 
    *  To find out how to configure the targets, please follow the link:
    *  https://kotlinlang.org/docs/reference/building-mpp-with-gradle.html#setting-up-targets */

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
