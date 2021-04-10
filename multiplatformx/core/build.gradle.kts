plugins {
    kotlin("multiplatform")
    kotlin("kapt")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
    google()
}

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            kotlin.setSrcDirs(listOf("src/main/kotlin"))
            resources.setSrcDirs(listOf("src/main/resources"))

            dependencies {
                implementation(kotlin("stdlib"))
                implementation(project(":multiplatformx-api"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf")
            }
        }

        val jvmTest by getting {
            kotlin.setSrcDirs(listOf("src/test/kotlin"))
            resources.setSrcDirs(listOf("src/test/resources"))

            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
                implementation(kotlin("test"))
                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter")
            }
        }
    }
}

kapt {
    includeCompileClasspath = true
}

tasks.getByName<Test>("jvmTest") {
    useJUnitPlatform()
}