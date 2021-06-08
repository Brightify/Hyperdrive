import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `maven-publish`
    id("org.jetbrains.dokka")
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

    apply(plugin = "org.jetbrains.dokka")

    group = "org.brightify.hyperdrive"

    tasks.withType(KotlinCompile::class).all {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs += listOf(
                // TODO: Find out why this doesn't work.
                // "-Xopt-in=kotlinx.serialization.ExperimentalSerializationApi"
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
