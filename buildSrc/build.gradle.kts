buildscript {
    repositories {
        mavenCentral()
        jcenter()
        google()
    }

    dependencies {
        classpath(kotlin("gradle-plugin", version = "1.4.21"))
    }
}

plugins {
    kotlin("jvm") version "1.4.21"
}

repositories {
    mavenCentral()
}
