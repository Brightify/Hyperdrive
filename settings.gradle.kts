pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        jcenter()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/brightify/p/hd/hyperdrive-snapshots") {
            name = "hyperdriveSnapshots"
            credentials(PasswordCredentials::class)
        }
    }
    plugins {
        id("org.brightify.hyperdrive.symbol-processing") version "1.0-SNAPSHOT"
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.android" || requested.id.name == "kotlin-android-extensions") {
                useModule("com.android.tools.build:gradle:4.0.1")
            }

            when (requested.id.id) {
                "symbol-processing" ->
                    useModule("com.google.devtools.ksp:symbol-processing:${requested.version}")
            }
        }
    }
}

enableFeaturePreview("GRADLE_METADATA")

rootProject.name = "Hyperdrive"

val mainModules = listOf(
    "plugin",
    "plugin-native",
    "plugin-gradle",
    "plugin-ide"
)

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
    "processor" to emptyList(),
    "integration" to emptyList()
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

val multiplatformXModules = listOf(
    "annotations",
    "api",
    "core",
    "plugin",
    "processor"
)

val multiplatformXProjects = multiplatformXModules.map { "multiplatformx-$it" to "multiplatformx/$it" }


val projects = mainProjects + krpcProjects + multiplatformXProjects

for ((name, path) in projects) {
    include(":$name")
    val project = project(":$name")
    project.projectDir = File(settingsDir, path)
}
