plugins {
    id("hyperdrive-multiplatform")
}

publishingMetadata {
    name = "Hyperdrive Logging"
    description = "WIP: Simple logging layer on top of Kermit."
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.coroutines.core)
                implementation(projects.kotlinUtils)
            }
        }
    }
}
