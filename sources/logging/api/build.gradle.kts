plugins {
    id("hyperdrive-multiplatform")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.coroutines.core)
                implementation(libs.stately.common)
                implementation(project(":kotlin-utils"))
            }
        }
    }
}
