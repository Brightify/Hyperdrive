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
    // Adds the version catalog to the convention plugins classpath
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.android.gradle.plugin)
    implementation(libs.intellij.gradle.plugin) {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation(libs.dokka.gradle.plugin) {
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
