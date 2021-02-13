import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `maven-publish`
}

buildscript {
    repositories {
        mavenCentral()
        google()
    }

    dependencies {
        classpath(kotlin("gradle-plugin", version = Versions.kotlin))
        classpath("com.android.tools.build:gradle:4.0.1")
    }
}

allprojects {
    group = "org.brightify.hyperdrive"
    version = "0.1.3"

    tasks.withType(KotlinCompile::class).all {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest> {
        useJUnitPlatform()
    }
}

subprojects {
    repositories {
        mavenCentral()
        jcenter()
        google()
    }

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