import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

plugins {
    id("build-setup") apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.plugin.serialization) apply false
}

allprojects {
    version = "1.0-SNAPSHOT"
    group = "org.brightify.hyperdrive.examples"


    tasks.withType<KotlinJvmCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs += listOf(
                "-Xopt-in=kotlin.RequiresOptIn",
            )
        }
    }

    afterEvaluate {
        extensions.findByType<JavaPluginExtension>()?.apply {
            toolchain {
               languageVersion.set(JavaLanguageVersion.of(11))
            }
        }

        extensions.findByType(org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension::class)?.apply {
            sourceSets.all {
                languageSettings {
                    progressiveMode = true
                }
            }
        }

        extensions.findByType(KotlinMultiplatformExtension::class)?.apply {
            sourceSets.all {
                languageSettings {
                    progressiveMode = true
                }
            }
        }
    }

    tasks.withType<KotlinJvmTest> {
        useJUnitPlatform()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

subprojects {
}
