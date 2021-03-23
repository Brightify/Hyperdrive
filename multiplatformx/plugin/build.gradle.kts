import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    `maven-publish`
}

tasks.withType(KotlinCompile::class).all {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

repositories {
    mavenCentral()
    jcenter()
    google()
    gradlePluginPortal()
}

dependencies {
    implementation(kotlin("stdlib"))
    compileOnly(kotlin("compiler-embeddable"))
    api(project(":plugin-api"))
    implementation(project(":multiplatformx-api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")

    compileOnly("com.google.auto.service:auto-service:${Versions.autoService}")
    kapt("com.google.auto.service:auto-service:${Versions.autoService}")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}") {
        version {
            strictly(Versions.coroutines)
        }
    }
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.3.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}