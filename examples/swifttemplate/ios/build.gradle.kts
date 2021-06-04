import org.jetbrains.kotlin.gradle.plugin.NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.tasks.FatFrameworkTask
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework.BitcodeEmbeddingMode

plugins {
    kotlin("multiplatform")
    id("com.chromaticnoise.multiplatform-swiftpackage")
    kotlin("plugin.serialization")
    id("org.brightify.hyperdrive")
}

multiplatformSwiftPackage {
    swiftToolsVersion("5.3")
    targetPlatforms {
        iOS { v("13") }
    }
}

kotlin {
    ios {
        binaries {
            framework {
                baseName = "SwiftTemplateExampleKit"
                embedBitcode = BitcodeEmbeddingMode.BITCODE
            }
        }
    }
    macosX64()

    val iphonesimulator = iosX64()
    val iphoneos = iosArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":example-swifttemplate-shared"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
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
                    // "-P", "plugin:org.brightify.hyperdrive.krpc:enabled=true",
                    // "-P", "plugin:org.brightify.hyperdrive.krpc:printIR=false",
                    // "-P", "plugin:org.brightify.hyperdrive.krpc:printKotlinLike=false"
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