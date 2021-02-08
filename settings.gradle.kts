pluginManagement {
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "kotlin-ksp",
                "org.jetbrains.kotlin.kotlin-ksp",
                "org.jetbrains.kotlin.ksp" -> {
                    useModule("com.google.devtools.ksp:symbol-processing:${requested.version}")
                }
            }
        }
    }

    plugins {
        id("org.brightify.hyperdrive.symbol-processing") version "1.0-SNAPSHOT"
    }

    repositories {
        gradlePluginPortal()
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
        google()
        maven("https://maven.pkg.jetbrains.space/brightify/p/hd/hyperdrive-snapshots") {
            name = "hyperdriveSnapshots"
            credentials(PasswordCredentials::class)
        }
    }
}
enableFeaturePreview("GRADLE_METADATA")

rootProject.name = "Hyperdrive"
include("krpc")

include("krpc:krpc-annotations")
project(":krpc:krpc-annotations").projectDir = file("krpc/annotations")

include("krpc:krpc-shared-api")
project(":krpc:krpc-shared-api").projectDir = file("krpc/shared/api")
include("krpc:krpc-shared-impl")
project(":krpc:krpc-shared-impl").projectDir = file("krpc/shared/impl")

include("krpc:krpc-client-api")
project(":krpc:krpc-client-api").projectDir = file("krpc/client/api")
include("krpc:krpc-client-impl")
project(":krpc:krpc-client-impl").projectDir = file("krpc/client/impl")

include("krpc:krpc-server-api")
project(":krpc:krpc-server-api").projectDir = file("krpc/server/api")
include("krpc:krpc-server-impl")
project(":krpc:krpc-server-impl").projectDir = file("krpc/server/impl")

include("krpc:krpc-processor")
project(":krpc:krpc-processor").projectDir = file("krpc/processor")

include("krpc:krpc-integration")
project(":krpc:krpc-integration").projectDir = file("krpc/integration")

