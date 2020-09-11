plugins {
    kotlin("jvm")
    kotlin("kapt")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":krpc:krpc-annotations"))
    implementation(project(":krpc:krpc-shared-api"))

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
