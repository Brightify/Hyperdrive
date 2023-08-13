pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }

    plugins {
    }
    resolutionStrategy {
//        eachPlugin {
//            if (requested.id.namespace == "com.android" || requested.id.name == "kotlin-android-extensions") {
//                useModule("com.android.tools.build:gradle:8.0.0")
//            }
//        }
    }

    includeBuild("build-setup")
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Hyperdrive"

include(
    ":compose",
    ":ide:android-studio",
    ":ide:intellij-idea",
    ":kotlin-utils",
    ":logging",
    ":plugin",
    ":runtime",
)
