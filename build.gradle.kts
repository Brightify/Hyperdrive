import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile

plugins {
    `maven-publish`
    signing
    id("org.jetbrains.dokka")
    kotlin("multiplatform") apply false
    kotlin("jvm") apply false
}


buildscript {
    apply(from = "compose-check.gradle.kts")

    val enableCompose: Boolean by extra
    if (enableCompose) {
        dependencies {
            classpath("com.android.tools.build:gradle:7.0.0")
        }
    }
}

allprojects {
    apply(plugin = "maven-publish")

    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }

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
    apply(plugin = "signing")

    version = rootProject.version

    val isSnapshot = project.version.withGroovyBuilder { "isSnapshot"() } as Boolean

    tasks.withType<PublishToMavenRepository> {
        onlyIf {
            fun urlExists(repositoryUrl: String) =
                try {
                    val connection = java.net.URL(repositoryUrl).openConnection() as java.net.HttpURLConnection

                    val (username, password) = with (repository.credentials) { username to password }

                    val base64EncodedCredentials = java.util.Base64.getEncoder().encodeToString("$username:$password".toByteArray())
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

            val pomFileName = "${publication.artifactId}-${publication.version}.pom"
            val artifactPath = "${project.group.toString().replace(".", "/")}/${publication.artifactId}/${publication.version}/${pomFileName}"
            val repositoryUrl = "${repository.url}/${artifactPath}"

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
            // Brightify repo.
            maven(
                if (isSnapshot) {
                    "https://maven.pkg.jetbrains.space/brightify/p/brightify/brightify-snapshots"
                } else {
                    "https://maven.pkg.jetbrains.space/brightify/p/brightify/brightify-releases"
                }
            ) {
                name = "brightify"

                val brightifyUsername: String by project
                val brightifyPassword: String by project
                credentials {
                    username = brightifyUsername
                    password = brightifyPassword
                }
            }

            // Maven central repo.
            val mavenCentralUsername: String? by project
            val mavenCentralPassword: String? by project
            if (mavenCentralUsername != null && mavenCentralPassword != null) {
                mavenCentral {
                    name = "mavenCentral"

                    credentials {
                        username = mavenCentralUsername
                        password = mavenCentralPassword
                    }
                }
            }
        }

        val mavenPublications = publications.filterIsInstance<MavenPublication>()

        mavenPublications
            .map { publication ->
                publication.pom {
                    name.set("Hyperdrive")
                    description.set("Kotlin Multiplatform Extensions")
                    url.set("https://hyperdrive.tools/")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("http://www.opensource.org/licenses/mit-license.php")
                        }
                    }
                    developers {
                        developer {
                            id.set("TadeasKriz")
                            name.set("Tadeas Kriz")
                            email.set("tadeas@brightify.org")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/Brightify/hyperdrive-kt.git")
                        developerConnection.set("scm:git:git@github.com:Brightify/hyperdrive-kt.git")
                        url.set("https://github.com/Brightify/hyperdrive-kt")
                    }
                }
            }

        signing {
            setRequired({
                !isSnapshot && gradle.taskGraph.hasTask("publish")
            })

            val mavenCentralSigningKey: String? by project
            val mavenCentralSigningPassword: String? by project
            useInMemoryPgpKeys(mavenCentralSigningKey, mavenCentralSigningPassword)

            mavenPublications.forEach(::sign)
        }
    }
}

tasks.dokkaHtmlMultiModule.configure {
    suppressInheritedMembers.set(true)
    suppressObviousFunctions.set(true)

    outputDirectory.set(rootDir.resolve("website/static/reference"))
}
