plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    // compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin-api:1.6.21")
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.android.gradle.plugin)
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
