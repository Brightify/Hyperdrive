import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("hyperdrive-base")
    kotlin("jvm")
    id("hyperdrive-publishable")
}

val libs = the<LibrariesForLibs>()

kotlin {
    jvmToolchain(11)
}

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly(kotlin("stdlib-jdk8"))
    compileOnly(kotlin("compiler-embeddable"))

    testImplementation(kotlin("test"))
    testImplementation(kotlin("compiler-embeddable"))
}

tasks.test {
    useJUnitPlatform()
}
