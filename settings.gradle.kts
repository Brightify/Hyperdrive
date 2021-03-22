pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        jcenter()
        mavenCentral()
    }
    plugins {
        id("org.jetbrains.dokka") version "1.4.20"
        id("org.brightify.hyperdrive.symbol-processing") version "1.0-SNAPSHOT"
        id("com.github.johnrengelman.shadow") version "6.1.0"
        id("com.github.gmazzo.buildconfig") version "2.0.2"
        kotlin("plugin.serialization") version "1.4.31"
        id("com.chromaticnoise.multiplatform-swiftpackage") version "2.0.3"
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.android" || requested.id.name == "kotlin-android-extensions") {
                useModule("com.android.tools.build:gradle:4.0.1")
            }

            when (requested.id.id) {
                "symbol-processing" ->
                    useModule("com.google.devtools.ksp:symbol-processing:${requested.version}")
                "kotlinx-atomicfu" ->
                    useModule("org.jetbrains.kotlinx:atomicfu-gradle-plugin:0.15.1")
            }
        }
    }
}

enableFeaturePreview("GRADLE_METADATA")

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

val mainModules = listOf<String>()

val mainProjects = mainModules.map {
    it to it
}

val krpcModules = listOf(
    "annotations" to emptyList(),
    "shared" to listOf(
        "api",
        "impl"
    ),
    "client" to listOf(
        "api",
        "impl"
    ),
    "server" to listOf(
        "api",
        "impl"
    ),
    "plugin" to emptyList(),
    "processor" to emptyList(),
    "integration" to emptyList(),
    "test" to emptyList()
)

val loggingModules = listOf(
    "api"
)
val loggingProjects = loggingModules.map { "logging-$it" to "logging/$it" }

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
