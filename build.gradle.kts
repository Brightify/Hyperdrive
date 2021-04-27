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

    group = "org.brightify.hyperdrive"

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

    version = rootProject.version

    val isSnapshot = project.version.withGroovyBuilder { "isSnapshot"() } as Boolean

    if (!isSnapshot) {
        tasks.named("publishToMavenLocal").get().dependsOn("checkMavenArtifact")
        tasks.named("publish").get().dependsOn("checkMavenArtifact")
    }

    val brightifyUsername: String by project
    val brightifyPassword: String by project
    val brightifyMavenUrl = if (isSnapshot) {
        "https://maven.pkg.jetbrains.space/brightify/p/brightify/brightify-snapshots"
    } else {
        "https://maven.pkg.jetbrains.space/brightify/p/brightify/brightify-releases"
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

    task("checkMavenArtifact") {
        group = "upload"

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

        doLast {
            println("# Maven artifact check for ${project.name} version ${project.version}")

            // We're not checking snapshot artifacts.
            if (isSnapshot) {
                println("\t- Skipping check for snapshot release.")
                return@doLast
            }

            val pomFileName = "${project.name}-${project.version}.pom"
            val artifactPath = "${project.group.toString().replace(".", "/")}/${project.name}/${project.version}/${pomFileName}"
            val repositoryUrl = "$brightifyMavenUrl/${artifactPath}"

            println("\t- Full repository URL: $repositoryUrl")

            if (urlExists(repositoryUrl)) {
                println("\t- Existing Maven artifact found. Stopping.")
                throw RuntimeException("Maven artifact for ${project.name} with version ${project.version} already exists.")
            } else {
                println("\t- No existing Maven artifact found. Proceeding.")
            }
        }
    }
}
