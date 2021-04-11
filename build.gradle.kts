import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `maven-publish`
    id("org.jetbrains.dokka")
    id("io.spring.dependency-management")
    kotlin("multiplatform") apply false
    kotlin("jvm") apply false
}

buildscript {
    repositories {
        mavenCentral()
        google()
    }

    dependencies {
        classpath(gradleKotlinDsl())
        classpath("com.android.tools.build:gradle:4.1.0")
    }
}

allprojects {
    repositories {
        mavenCentral()
        jcenter()
        google()
        gradlePluginPortal()
    }

    group = "org.brightify.hyperdrive"
    version = "0.1.31"

    apply(plugin = "org.jetbrains.dokka")

    tasks.withType(KotlinCompile::class).all {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs += listOf(
                "-Xopt-in=kotlinx.serialization.ExperimentalSerializationApi"
            )
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest> {
        useJUnitPlatform()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

subprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "io.spring.dependency-management")

    dependencyManagement {
        dependencies {
            dependencySet("org.jetbrains.kotlin:1.4.32") {
                entry("kotlin-gradle-plugin-api")
            }

            dependencySet("org.jetbrains.kotlinx:1.4.3-native-mt") {
                entry("kotlinx-coroutines-core")
                entry("kotlinx-coroutines-test")
            }

            dependencySet("org.jetbrains.kotlinx:1.1.0") {
                entry("kotlinx-serialization-core")
                entry("kotlinx-serialization-json")
                entry("kotlinx-serialization-protobuf")
            }

            dependencySet("co.touchlab:1.1.5") {
                entry("stately-common")
            }

            dependencySet("io.ktor:1.5.2") {
                entry("ktor-client-websockets")
                entry("ktor-server-core")
                entry("ktor-server-netty")
                entry("ktor-websockets")
                entry("ktor-client-cio")
                entry("ktor-client-js")
            }

            dependency("com.google.auto.service:auto-service:1.0-rc6")

            dependency("com.github.tschuchortdev:kotlin-compile-testing:1.3.6")
            dependency("org.junit.jupiter:junit-jupiter:5.6.2")

            dependencySet("io.kotest:4.4.3") {
                entry("kotest-assertions-core") {
                    exclude("org.jetbrains.kotlinx:kotlinx-coroutines-core")
                }
                entry("kotest-property") {
                    exclude("org.jetbrains.kotlinx:kotlinx-coroutines-core")
                }
                entry("kotest-runner-junit5") {
                    exclude("org.jetbrains.kotlinx:kotlinx-coroutines-core")
                }
            }
        }
    }

    publishing {
        repositories {
            val brightifyUsername: String by project
            val brightifyPassword: String by project

            val brightifyMavenUrl = if (project.version.toString().endsWith("-SNAPSHOT")) {
                "https://maven.pkg.jetbrains.space/brightify/p/brightify/brightify-snapshots"
            } else {
                "https://maven.pkg.jetbrains.space/brightify/p/brightify/brightify-releases"
            }
            maven(brightifyMavenUrl) {
                credentials {
                    username = brightifyUsername
                    password = brightifyPassword
                }
            }
        }
    }
}