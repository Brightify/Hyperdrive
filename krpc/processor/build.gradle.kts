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
    implementation("com.squareup:kotlinpoet:0.7.0")

    implementation("org.jetbrains.kotlin:kotlin-symbol-processing-api:1.4.0-dev-experimental-20200828")

    implementation("com.google.auto.service:auto-service:1.0-rc4")
    kapt("com.google.auto.service:auto-service:1.0-rc4")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.2.10")
    testImplementation("org.jetbrains.kotlin:kotlin-ksp:1.4.0-dev-experimental-20200828")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing-ksp:1.2.10")
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
        if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-symbol-processing-api") {
            useVersion("1.4.0-dev-experimental-20200828")
            because("Aligns versions across the project")
        }
        if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-ksp") {
            useVersion("1.4.0-dev-experimental-20200828")
            because("Aligns versions across the project")
        }
    }
}