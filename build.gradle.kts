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
    version = "0.1.0-SNAPSHOT"

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
            maven("https://maven.pkg.jetbrains.space/brightify/p/hd/hyperdrive-snapshots") {
                name = "hyperdriveSnapshots"
                credentials(PasswordCredentials::class)
            }
        }
    }
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}
