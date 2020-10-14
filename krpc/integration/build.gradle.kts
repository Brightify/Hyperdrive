plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("org.brightify.hyperdrive")
    kotlin("plugin.serialization") version "1.4.0"
}

val serialization_version = "1.0.0"
dependencies {
    testImplementation(project(":krpc:krpc-annotations"))
    testImplementation(project(":krpc:krpc-server-api"))
    testImplementation(project(":krpc:krpc-shared-api"))
    testImplementation(project(":krpc:krpc-client-api"))

    testImplementation(project(":krpc:krpc-server-impl"))
    testImplementation(project(":krpc:krpc-shared-impl"))
    testImplementation(project(":krpc:krpc-client-impl"))

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.3.9-native-mt")

    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$serialization_version")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_version")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$serialization_version")

    "ksp"(project(":krpc:krpc-processor"))
}

kotlin {
    sourceSets.main {
        kotlin.srcDir(File(buildDir, "generated/ksp/src/main/kotlin"))
    }

    sourceSets.test {
        kotlin.srcDir(File(buildDir, "generated/ksp/src/test/kotlin"))
    }
}

kapt {
    includeCompileClasspath = true
}

tasks {
    test {
        useJUnitPlatform()
    }
}
