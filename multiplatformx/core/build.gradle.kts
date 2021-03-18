plugins {
    kotlin("multiplatform")
    kotlin("kapt")
    kotlin("plugin.serialization") version Versions.kotlin
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
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}") {
                    version {
                        strictly(Versions.coroutines)
                    }
                }

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.0.1")
            }
        }

        val jvmTest by getting {
            kotlin.setSrcDirs(listOf("src/test/kotlin"))
            resources.setSrcDirs(listOf("src/test/resources"))

            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter:5.6.2")
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