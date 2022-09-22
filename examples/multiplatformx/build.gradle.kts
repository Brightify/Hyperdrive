import org.jetbrains.kotlin.gradle.plugin.NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.tasks.FatFrameworkTask
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework.BitcodeEmbeddingMode

plugins {
//    id("com.android.library")
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.swiftpackage)
}

tasks.publish {
    enabled = false
}

multiplatformSwiftPackage {
    swiftToolsVersion("5.3")
    targetPlatforms {
        iOS { v("13") }
    }
}

dependencies {
    // PLUGIN_CLASSPATH_CONFIGURATION_NAME(project(":plugin-impl"))
    // NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME(project(":plugin-impl"))
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
    jvm()
//    android()
    ios {
        binaries {
            framework {
                baseName = "MultiplatformXKit"
                embedBitcode = BitcodeEmbeddingMode.BITCODE
            }
        }
    }

    val iphonesimulator = iosX64()
    val iphoneos = iosArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":multiplatformx-api"))

                implementation(libs.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val iosMain by getting
        val iosTest by getting
    }

    targets.all {
        compilations.all {
            kotlinOptions {
                freeCompilerArgs += listOf(
                    "-P", "plugin:org.brightify.hyperdrive.multiplatformx:enabled=true",
                    "-P", "plugin:org.brightify.hyperdrive.multiplatformx:viewmodel.enabled=true",
                    "-P", "plugin:org.brightify.hyperdrive.multiplatformx:autofactory.enabled=true"
                )
            }
        }
    }

    val packFramework by tasks.creating(FatFrameworkTask::class) {
        group = "build"

        val mode = System.getenv("CONFIGURATION") ?: "DEBUG"

        baseName = "MultiplatformXKit"
        destinationDir = File(buildDir, "xcode-frameworks")
        from(
            iphonesimulator.binaries.getFramework(mode),
            iphoneos.binaries.getFramework(mode)
        )
        inputs.property("mode", mode)
    }
}
