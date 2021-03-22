plugins {
    kotlin("jvm")
    kotlin("kapt")
    `maven-publish`
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":krpc-annotations"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
    implementation("com.squareup:kotlinpoet:1.7.2")

    implementation("com.google.devtools.ksp:symbol-processing-api:${Versions.symbolProcessing}")

    implementation("com.google.auto.service:auto-service:1.0-rc6")
    kapt("com.google.auto.service:auto-service:1.0-rc6")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}") {
        version {
            strictly(Versions.coroutines)
        }
    }
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.3.6")
    testImplementation("com.google.devtools.ksp:symbol-processing-api:${Versions.symbolProcessing}")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing-ksp:1.3.6")
    testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
}

kapt {
    includeCompileClasspath = true
}

tasks {
    test {
        useJUnitPlatform()
    }
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.google.devtools.ksp" && requested.name == "symbol-processing-api") {
            useVersion(Versions.symbolProcessing)
            because("Aligns versions across the project")
        }
        if (requested.group == "com.google.devtools.ksp" && requested.name == "symbol-processing") {
            useVersion(Versions.symbolProcessing)
            because("Aligns versions across the project")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}