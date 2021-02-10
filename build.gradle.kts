import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    `maven-publish`
}

buildscript {
    repositories {
        mavenCentral()
        google()
    }

    dependencies {
        classpath(kotlin("gradle-plugin", version = Versions.kotlin))
    }
}

allprojects {
    group = "org.brightify.hyperdrive"
    version = "0.1.0"

    tasks.withType(KotlinCompile::class).all {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }
}

subprojects {
    repositories {
        mavenCentral()
        google()
    }

    apply(plugin = "maven-publish")

    publishing {
        repositories {
//            val brightifyMavenUrl = if (project.version.endsWith("-SNAPSHOT")) {
//                "https://maven.pkg.jetbrains.space/brightify/p/brightify/brightify-snapshots"
//            } else {
//                "https://maven.pkg.jetbrains.space/brightify/p/brightify/brightify-releases"
//            }
//            maven(brightifyMavenUrl) {
//                credentials {
//                    username = project.brightifyUsername
//                    password = project.brightifyPassword
//                }
//            }
        }
    }
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}
