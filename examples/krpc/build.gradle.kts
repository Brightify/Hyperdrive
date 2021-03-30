import org.jetbrains.kotlin.gradle.plugin.NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.tasks.FatFrameworkTask
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework.BitcodeEmbeddingMode

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    id("com.chromaticnoise.multiplatform-swiftpackage")
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
    PLUGIN_CLASSPATH_CONFIGURATION_NAME(project(":plugin-impl"))
    NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME(project(":plugin-impl-native"))
}

android {
    compileSdkVersion(30)

    dexOptions {
        javaMaxHeapSize = "2g"
    }

    defaultConfig {
        minSdkVersion(16)
    }

    sourceSets {
        val main by getting
        main.manifest.srcFile("src/androidMain/AndroidManifest.xml")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

kotlin {
    jvm()
    android()
    ios {
        binaries {
            framework {
                baseName = "KrpcExampleKit"
                embedBitcode = BitcodeEmbeddingMode.BITCODE
            }
        }
    }

    val iphonesimulator = iosX64()
    val iphoneos = iosArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":krpc-shared-api"))
                implementation(project(":krpc-shared-impl"))
                implementation(project(":krpc-test"))
                implementation(project(":logging-api"))

                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.serialization}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}") {
                    version {
                        strictly(Versions.coroutines)
                    }
                }
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
        val androidMain by getting
        val androidTest by getting

        val iosMain by getting
        val iosTest by getting
    }

    sourceSets.all {
        languageSettings.apply {
            progressiveMode = true
        }
    }

    targets.all {
        compilations.all {
            kotlinOptions {
                freeCompilerArgs += listOf(
                    "-P", "plugin:org.brightify.hyperdrive.krpc:enabled=true"
                )
            }
        }
    }

    val packFramework by tasks.creating(FatFrameworkTask::class) {
        group = "build"

        val mode = System.getenv("CONFIGURATION") ?: "DEBUG"

        baseName = "KrpcKit"
        destinationDir = File(buildDir, "xcode-frameworks")
        from(
            iphonesimulator.binaries.getFramework(mode),
            iphoneos.binaries.getFramework(mode)
        )
        inputs.property("mode", mode)
    }
}