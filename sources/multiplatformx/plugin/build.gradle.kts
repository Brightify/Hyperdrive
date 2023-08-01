plugins {
    id("hyperdrive-kotlin-plugin")
}

dependencies {
    api(project(":plugin-api"))
    implementation(project(":multiplatformx-api"))
    implementation(libs.coroutines.core)

    testImplementation(libs.coroutines.core)
    testImplementation(libs.compile.testing)
}
