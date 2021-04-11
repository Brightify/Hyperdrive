pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        jcenter()
        mavenCentral()
    }

    val kotlinVersion: String by settings
    plugins {
        id("org.jetbrains.dokka") version "1.4.30"
        id("com.github.johnrengelman.shadow") version "6.1.0"
        id("com.github.gmazzo.buildconfig") version "2.0.2"
        kotlin("plugin.serialization") version kotlinVersion
        kotlin("multiplatform") version kotlinVersion
        kotlin("jvm") version kotlinVersion
        id("com.chromaticnoise.multiplatform-swiftpackage") version "2.0.3"
        id("com.github.gmazzo.buildconfig") version "2.0.2"
        id("org.jetbrains.intellij") version "0.6.5"
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.android" || requested.id.name == "kotlin-android-extensions") {
                useModule("com.android.tools.build:gradle:4.1.0")
            }
        }
    }
}

enableFeaturePreview("VERSION_CATALOGS")

rootProject.name = "Hyperdrive"

val pluginModules = listOf(
    "api",
    "impl",
    "impl-native",
    "gradle",
    "idea"
)

val pluginProjects = pluginModules.map {
    "plugin-$it" to "plugin/$it"
}

val mainModules = listOf(
    "kotlin-utils"
)

val mainProjects = mainModules.map {
    it to it
}

val krpcModules = listOf(
    "annotations" to emptyList(),
    "shared" to listOf(
        "api",
        "impl",
        "impl-ktor"
    ),
    "client" to listOf(
        "api",
        "impl",
        "impl-ktor"
    ),
    "server" to listOf(
        "api",
        "impl",
        "impl-ktor"
    ),
    "plugin" to emptyList(),
    "integration" to emptyList(),
    "test" to emptyList()
)

val krpcProjects = krpcModules.flatMap {
    val (module, submodules) = it
    if (submodules.isEmpty()) {
        listOf("krpc-$module" to "krpc/$module")
    } else {
        submodules.map { submodule ->
            "krpc-$module-$submodule" to "krpc/$module/$submodule"
        }
    }
}

val loggingModules = listOf(
    "api"
)

val loggingProjects = loggingModules.map { "logging-$it" to "logging/$it" }

val multiplatformXModules = listOf(
    "api",
    "core",
    "plugin"
)

val multiplatformXProjects = multiplatformXModules.map { "multiplatformx-$it" to "multiplatformx/$it" }

val exampleModules = listOf(
    "krpc",
    "multiplatformx"
)

val exampleProjects = exampleModules.map { "example-$it" to "examples/$it" }

val projects = listOf(
    mainProjects,
    pluginProjects,
    krpcProjects,
    multiplatformXProjects,
    exampleProjects,
    loggingProjects
).flatten()

for ((name, path) in projects) {
    include(":$name")
    val project = project(":$name")
    project.projectDir = File(settingsDir, path)
}
