import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile

plugins {
    `maven-publish`
    id("org.jetbrains.dokka")
    kotlin("multiplatform") apply false
    kotlin("jvm") apply false
}


buildscript {
    apply(from = "compose-check.gradle.kts")

    val enableCompose: Boolean by extra
    if (enableCompose) {
        dependencies {
            classpath("com.android.tools.build:gradle:7.0.0-beta05")
        }
    }
}

allprojects {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }

    apply(plugin = "org.jetbrains.dokka")

    group = "org.brightify.hyperdrive"

    tasks.withType(KotlinJvmCompile::class).all {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs += listOf(
                "-Xopt-in=kotlin.RequiresOptIn",
                "-Xopt-in=kotlinx.serialization.ExperimentalSerializationApi",
            )
        }
    }

    afterEvaluate {
        extensions.findByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class)?.sourceSets?.all {
            languageSettings {
                useExperimentalAnnotation("kotlin.RequiresOptIn")
                useExperimentalAnnotation("kotlinx.serialization.ExperimentalSerializationApi")
            }
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

    version = rootProject.version

    val isSnapshot = project.version.withGroovyBuilder { "isSnapshot"() } as Boolean

    val brightifyUsername: String by project
    val brightifyPassword: String by project
    val brightifyMavenUrl = if (isSnapshot) {
        "https://maven.pkg.jetbrains.space/brightify/p/brightify/brightify-snapshots"
    } else {
        "https://maven.pkg.jetbrains.space/brightify/p/brightify/brightify-releases"
    }

    tasks.withType<PublishToMavenRepository> {
        onlyIf {
            fun urlExists(repositoryUrl: String) =
                try {
                    val connection = java.net.URL(repositoryUrl).openConnection() as java.net.HttpURLConnection

                    val base64EncodedCredentials = java.util.Base64.getEncoder().encodeToString("$brightifyUsername:$brightifyPassword".toByteArray())
                    connection.setRequestProperty("Authorization", "Basic $base64EncodedCredentials")
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.requestMethod = "HEAD"

                    val responseCode = connection.responseCode

                    if (responseCode == 401) {
                        throw RuntimeException("Unauthorized MavenUser user. Please provide valid username and password.")
                    }

                    responseCode == 200
                } catch (ignored: java.io.IOException) {
                    println("Ignoring exception: $ignored")
                    false
                } catch (e: Exception) {
                    false
                }

            println("# Maven artifact check for ${this.publication.artifactId} version ${this.publication.version}")

            // We're not checking snapshot artifacts.
            if (isSnapshot) {
                println("\t- Skipping check for snapshot release.")
                return@onlyIf true
            }

            val pomFileName = "${this.publication.artifactId}-${this.publication.version}.pom"
            val artifactPath = "${project.group.toString().replace(".", "/")}/${this.publication.artifactId}/${this.publication.version}/${pomFileName}"
            val repositoryUrl = "${this.repository.url}/${artifactPath}"

            println("\t- Full repository URL: $repositoryUrl")

            return@onlyIf if (urlExists(repositoryUrl)) {
                println("\t- Existing Maven artifact found. Stopping.")
                false
            } else {
                println("\t- No existing Maven artifact found. Proceeding.")
                true
            }
        }
    }

    publishing {
        repositories {
            maven(brightifyMavenUrl) {
                credentials {
                    username = brightifyUsername
                    password = brightifyPassword
                }
            }
        }
    }
}
