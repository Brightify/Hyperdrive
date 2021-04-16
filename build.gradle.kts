import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `maven-publish`
    id("org.jetbrains.dokka")
    kotlin("multiplatform") apply false
    kotlin("jvm") apply false
}

buildscript {
    repositories {
        mavenCentral()
        google()
    }

    dependencies {
        classpath(gradleKotlinDsl())
        classpath("com.android.tools.build:gradle:4.1.0")
    }
}

allprojects {
    repositories {
        mavenCentral()
        jcenter()
        google()
        gradlePluginPortal()
    }

    group = "org.brightify.hyperdrive"
    version = "0.1.40"

    apply(plugin = "org.jetbrains.dokka")

    tasks.withType(KotlinCompile::class).all {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs += listOf(
                "-Xopt-in=kotlinx.serialization.ExperimentalSerializationApi"
            )
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest> {
        useJUnitPlatform()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

subprojects {
    apply(plugin = "maven-publish")

    publishing {
        repositories {
            val brightifyUsername: String by project
            val brightifyPassword: String by project

            val brightifyMavenUrl = if (project.version.toString().endsWith("-SNAPSHOT")) {
                "https://maven.pkg.jetbrains.space/brightify/p/brightify/brightify-snapshots"
            } else {
                "https://maven.pkg.jetbrains.space/brightify/p/brightify/brightify-releases"
            }
            maven(brightifyMavenUrl) {
                credentials {
                    username = brightifyUsername
                    password = brightifyPassword
                }
            }
        }
    }
}