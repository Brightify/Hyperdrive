buildscript {
    repositories {
        mavenCentral()
        jcenter()
        google()
    }

    dependencies {
        classpath(kotlin("gradle-plugin", version = "1.4.30"))
    }
}

plugins {
    kotlin("jvm") version "1.4.30"
}

repositories {
    mavenCentral()
}
