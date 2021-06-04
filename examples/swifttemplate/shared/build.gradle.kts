import org.jetbrains.kotlin.gradle.plugin.NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.tasks.FatFrameworkTask
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework.BitcodeEmbeddingMode

plugins {
    kotlin("multiplatform")
    id("org.brightify.hyperdrive")
}

kotlin {
    jvm()
    ios()
    macosX64()

    val iphonesimulator = iosX64()
    val iphoneos = iosArm64()

    sourceSets {
        val commonMain by getting {

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
}