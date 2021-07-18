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

android {
    compileSdkVersion(30)

    defaultConfig {
        minSdkVersion(21)
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
        useIR = true
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerVersion = kotlinVersion
        kotlinCompilerExtensionVersion = libs.versions.compose.get()
    }
}

dependencies {
    implementation(project(":multiplatformx-api"))
    implementation(libs.compose.runtime)
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
