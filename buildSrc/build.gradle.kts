plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.30")
    implementation("com.android.tools.build:gradle:7.0.2")
    compileOnly(gradleApi())
    compileOnly(localGroovy())
}