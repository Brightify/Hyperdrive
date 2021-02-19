buildscript {
    repositories {
        mavenCentral()
        jcenter()
        google()
    }

    dependencies {
        classpath(kotlin("gradle-plugin", version = "1.4.21-2"))
    }
}

plugins {
    kotlin("jvm") version "1.4.21-2"
}

repositories {
    mavenCentral()
}
