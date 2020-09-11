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
}

subprojects {
    repositories {
        mavenCentral()
        google()
    }
}
