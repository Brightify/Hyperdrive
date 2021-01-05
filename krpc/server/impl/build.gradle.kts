plugins {
    kotlin("jvm")
    kotlin("kapt")
}

val ktor_version = "1.5.0"
val serialization_version = "1.0.1"

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":krpc:krpc-server-api"))
    implementation(project(":krpc:krpc-shared-api"))
    implementation(project(":krpc:krpc-annotations"))

    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-websockets:$ktor_version")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$serialization_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$serialization_version")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
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
