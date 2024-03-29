import org.brightify.hyperdrive.configurePlatforms

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    configurePlatforms(appleSilicon = true)
    explicitApi()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":multiplatformx-keyvalue"))
                api(libs.multiplatformSettings.core)
                implementation(libs.multiplatformSettings.coroutines)
                implementation(libs.coroutines.core)
                implementation(libs.serialization.core)
                implementation(libs.serialization.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.coroutines.core)
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.junit.jupiter)
                implementation(libs.coroutines.test)
            }
        }
    }
}
