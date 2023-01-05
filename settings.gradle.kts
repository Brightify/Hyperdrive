pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }

    plugins {
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.android" || requested.id.name == "kotlin-android-extensions") {
                useModule("com.android.tools.build:gradle:7.3.0")
            }
        }
    }
}

rootProject.name = "Hyperdrive"

includeBuild("build-setup")
includeBuild("sources")
includeBuild("examples")
