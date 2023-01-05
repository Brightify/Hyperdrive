plugins {
    kotlin("jvm")
    kotlin("kapt")
}

dependencies {
    api(project(":plugin-api"))
    compileOnly(kotlin("compiler-embeddable"))
    implementation(kotlin("serialization"))

    implementation(project(":krpc-shared-api"))
    implementation(project(":krpc-client-api"))
    implementation(project(":krpc-annotations"))

    compileOnly(libs.auto.service)
    kapt(libs.auto.service)

    testImplementation(project(":krpc-shared-api"))
    testImplementation(project(":krpc-shared-impl"))
    testImplementation(project(":krpc-server-api"))
    testImplementation(project(":krpc-server-impl"))
    testImplementation(project(":krpc-client-api"))
    testImplementation(project(":krpc-client-impl"))
    testImplementation(project(":krpc-annotations"))
    testImplementation(project(":krpc-test"))

    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))
    testImplementation(kotlin("test-junit5"))
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.bundles.serialization)
    testImplementation(libs.compile.testing)
    testImplementation(libs.junit.jupiter)
    testImplementation("com.benasher44:uuid:0.2.3")

    testImplementation("io.github.classgraph:classgraph:4.8.105")
}
