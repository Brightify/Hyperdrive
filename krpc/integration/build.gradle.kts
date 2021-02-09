plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("org.brightify.hyperdrive.symbol-processing")
    kotlin("plugin.serialization") version "1.4.20"
}

val serialization_version = "1.0.1"
dependencies {
    testImplementation(project(":krpc-annotations"))
    testImplementation(project(":krpc-server-api"))
    testImplementation(project(":krpc-shared-api"))
    testImplementation(project(":krpc-client-api"))

    testImplementation(project(":krpc-server-impl"))
    testImplementation(project(":krpc-shared-impl"))
    testImplementation(project(":krpc-client-impl"))

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.4.2-native-mt")

    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$serialization_version")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_version")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$serialization_version")

    "ksp"(project(":krpc-processor"))
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
