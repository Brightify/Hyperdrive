import org.brightify.hyperdrive.configurePlatforms

plugins {
    id("hyperdrive-multiplatform")
    alias(libs.plugins.kotest)
}

kotlin {
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
                implementation(kotlin("reflect"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.bundles.kotest.common)
                implementation(libs.coroutines.test)
                implementation(libs.bundles.kotest.jvm)
                implementation(libs.coroutines.test)
            }
        }
    }
}

tasks.dokkaHtmlPartial.configure {
    moduleName.set("Singularity API (${moduleName.get()})")
}
