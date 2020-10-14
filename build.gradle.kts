import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
}

buildscript {
    repositories {
        mavenCentral()
        google()
    }

    dependencies {
        classpath(kotlin("gradle-plugin", version = "1.4.0"))
    }
}

allprojects {
    group = "org.brightify.hyperdrive"
    version = "1.0-SNAPSHOT"

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
}
