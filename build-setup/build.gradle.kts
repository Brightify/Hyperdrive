plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.android.gradle.plugin)
    implementation(libs.intellij.gradle.plugin) {
        exclude(group = "org.jetbrains.kotlin")
    }
    compileOnly(gradleApi())
    compileOnly(localGroovy())
}

gradlePlugin {
    plugins {
        create("build-setup") {
            id = "build-setup"
            implementationClass = "org.brightify.hyperdrive.BuildSetupPlugin"
            displayName = "Hyperdrive Build Setup Plugin"
            description = "Hyperdrive Build Setup Plugin"
        }
    }
}
