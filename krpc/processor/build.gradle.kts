plugins {
    kotlin("jvm")
    kotlin("kapt")
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":krpc:krpc-annotations"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9")
    implementation("com.squareup:kotlinpoet:1.6.0")

    implementation("com.google.devtools.ksp:symbol-processing-api:1.4.20-dev-experimental-20201222")

    implementation("com.google.auto.service:auto-service:1.0-rc4")
    kapt("com.google.auto.service:auto-service:1.0-rc4")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.2.20")
    testImplementation("com.google.devtools.ksp:symbol-processing-api:1.4.20-dev-experimental-20201222")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing-ksp:1.2.20")
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
            useVersion("1.4.20-dev-experimental-20201222")
            because("Aligns versions across the project")
        }
        if (requested.group == "com.google.devtools.ksp" && requested.name == "symbol-processing") {
            useVersion("1.4.20-dev-experimental-20201222")
            because("Aligns versions across the project")
        }
    }
}