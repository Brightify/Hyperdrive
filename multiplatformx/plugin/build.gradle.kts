plugins {
    kotlin("jvm")
    kotlin("kapt")
}

dependencies {
    implementation(kotlin("stdlib"))
    compileOnly(kotlin("compiler-embeddable"))
    api(project(":plugin-api"))
    implementation(project(":multiplatformx-api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    compileOnly("com.google.auto.service:auto-service")
    kapt("com.google.auto.service:auto-service")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing")
    testImplementation("org.junit.jupiter:junit-jupiter")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}