import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// TODO: Fix Android. For some reason kotlin thinks there's no target when Android is enabled.
plugins {
//    id("com.android.library")
    kotlin("multiplatform")
}

//android {
//    compileSdkVersion(30)
//
//    dexOptions {
//        javaMaxHeapSize = "2g"
//    }
//
//    defaultConfig {
//        minSdkVersion(16)
//    }
//
//    sourceSets {
//        val main by getting
//        main.manifest.srcFile("src/androidMain/AndroidManifest.xml")
//    }
//    compileOptions {
//        sourceCompatibility = JavaVersion.VERSION_1_8
//        targetCompatibility = JavaVersion.VERSION_1_8
//    }
//}

kotlin {

//    android()
    ios()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":multiplatformx-annotations"))
                implementation(project(":multiplatformx-api"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-junit"))
                implementation(kotlin("test-annotations-common"))
            }
        }
//        val androidMain by getting
//        val androidTest by getting

        val iosMain by getting
        val iosTest by getting
    }

    sourceSets.all {
        languageSettings.apply {
            progressiveMode = true
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        useIR = true
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile> {
    compilerPluginClasspath = files(project(":plugin-impl-native").buildDir)

}
