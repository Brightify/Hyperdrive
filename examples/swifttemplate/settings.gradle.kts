pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        jcenter()
        mavenCentral()
    }

    val kotlinVersion: String by settings
    plugins {
        kotlin("plugin.serialization") version kotlinVersion
        kotlin("multiplatform") version kotlinVersion
        kotlin("jvm") version kotlinVersion
        id("com.chromaticnoise.multiplatform-swiftpackage") version "2.0.3"
    }
}

val swiftTemplateExampleProjects = listOf(
    "example-swifttemplate-shared" to "shared",
    "example-swifttemplate-ios" to "ios",
)

val projects = listOf(
    swiftTemplateExampleProjects,
).flatten()

for ((name, path) in projects) {
    include(":$name")
    val project = project(":$name")
    project.projectDir = File(settingsDir, path)
}

enableFeaturePreview("VERSION_CATALOGS")

includeBuild("../..")

