import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

plugins {
    id("com.android.library")
    kotlin("android")
    `maven-publish`
}

repositories {
    mavenCentral()
    google()
}

val kotlinVersion: String by project

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

android {
    compileSdk = 30

    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += listOf(
            "-P", "plugin:org.brightify.hyperdrive.multiplatformx:enabled=true",
            "-P", "plugin:org.brightify.hyperdrive.multiplatformx:viewmodel.enabled=true",
            "-P", "plugin:org.brightify.hyperdrive.multiplatformx:viewmodel.composable-auto-observe.enabled=true",
            "-P", "plugin:org.brightify.hyperdrive.multiplatformx:autofactory.enabled=true"
        )
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

}

dependencies {
    implementation(project(":multiplatformx-api"))
    implementation(libs.compose.runtime)

    androidTestImplementation(libs.compose.material)

    PLUGIN_CLASSPATH_CONFIGURATION_NAME(project(":multiplatformx-plugin"))
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
            }
            create<MavenPublication>("debug") {
                from(components["debug"])

                artifactId = project.name + "-debug"
            }
        }
    }
}
