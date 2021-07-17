plugins {
    kotlin("multiplatform")
    kotlin("kapt")
    kotlin("plugin.serialization")
    id("com.android.library")
}

repositories {
    mavenCentral()
    google()
}

android {
    compileSdkVersion(30)

    defaultConfig {
        minSdkVersion(16)
    }

    sourceSets {
        val main by getting
        main.manifest.srcFile("src/androidMain/AndroidManifest.xml")
    }
}


kotlin {
    android()

    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(project(":multiplatformx-api"))
                implementation(libs.compose.runtime)
            }
        }

        val androidTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.junit.jupiter)
            }
        }
    }
}

kapt {
    includeCompileClasspath = true
}
