plugins {
    id("org.barfuin.gradle.taskinfo") version "1.1.1"
}

allprojects {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}