plugins {
    kotlin("multiplatform")
    kotlin("kapt")
    idea
}

repositories {
    mavenCentral()
    google()
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }

    sourceSets {
        val jvmMain by getting {
            kotlin.setSrcDirs(listOf("src/main/kotlin"))
            resources.setSrcDirs(listOf("src/main/resources"))

            dependencies {
                implementation(kotlin("stdlib"))
                implementation(kotlin("compiler-embeddable"))
                implementation(project(":multiplatformx-annotations"))
                implementation(project(":multiplatformx-core"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
                implementation("com.squareup:kotlinpoet:1.7.2")

                implementation("com.google.devtools.ksp:symbol-processing-api:${Versions.symbolProcessing}")
                implementation("com.google.devtools.ksp:symbol-processing:${Versions.symbolProcessing}")

                compileOnly("com.google.auto.service:auto-service:${Versions.autoService}")
                configurations.getByName("kapt").dependencies.add(
                    implementation("com.google.auto.service:auto-service:${Versions.autoService}")
                )
            }
        }

        val jvmTest by getting {
            kotlin.setSrcDirs(listOf("src/test/kotlin"))
            resources.setSrcDirs(listOf("src/test/resources"))

            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit5"))
                implementation(kotlin("compiler-embeddable"))
                implementation("com.github.tschuchortdev:kotlin-compile-testing:1.3.1")
                implementation("com.github.tschuchortdev:kotlin-compile-testing-ksp:1.3.1")
                implementation("org.junit.jupiter:junit-jupiter:5.6.2")
            }
        }
    }
}

kapt {
    includeCompileClasspath = true
}

dependencies {
    "kapt"("com.google.auto.service:auto-service:${Versions.autoService}")
}

tasks.getByName<Test>("jvmTest") {
    useJUnitPlatform()
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (
            (requested.group == "com.google.devtools.ksp" && requested.name == "symbol-processing-api") ||
            (requested.group == "com.google.devtools.ksp" && requested.name == "symbol-processing")
        ) {
            useVersion(Versions.symbolProcessing)
            because("Aligns versions across the project")
        }
    }
}
