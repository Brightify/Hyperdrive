plugins {
    kotlin("jvm")
    kotlin("kapt")
}

dependencies {
    compileOnly(kotlin("compiler-embeddable"))
    api(project(":plugin-api"))
    implementation(project(":multiplatformx-api"))
    implementation(libs.coroutines.core)

    compileOnly(libs.auto.service)
    kapt(libs.auto.service)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation(libs.coroutines.core)
    testImplementation(libs.compile.testing)
    testImplementation(libs.junit.jupiter)
}
