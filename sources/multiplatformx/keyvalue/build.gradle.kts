import org.brightify.hyperdrive.configurePlatforms

plugins {
    id("hyperdrive-multiplatform")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.stately.common)
                implementation(libs.coroutines.core)
                implementation(libs.serialization.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.coroutines.test)
            }
        }
    }
}

tasks.dokkaHtmlPartial.configure {
    moduleName.set("Singularity Key-Value Storage (${moduleName.get()})")
}
