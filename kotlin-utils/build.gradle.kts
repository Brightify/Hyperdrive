plugins {
    id("hyperdrive-multiplatform")
}

publishingMetadata {
    name = "Hyperdrive Internal Utils"
    description = "Module containing various utils used throughout Hyperdrive."
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.coroutines.core)
            }
        }
    }
}
