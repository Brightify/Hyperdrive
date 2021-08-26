plugins {
    kotlin("jvm")
    kotlin("kapt")
}

dependencies {
    api(project(":plugin-api"))
    implementation(kotlin("stdlib"))
    compileOnly(kotlin("compiler-embeddable"))
    implementation(kotlin("serialization"))
    implementationWorkaround(project(":krpc-shared-api"))
    implementationWorkaround(project(":krpc-client-api"))
    implementationWorkaround(project(":krpc-annotations"))

    compileOnly(libs.auto.service)
    kapt(libs.auto.service)

    testImplementationWorkaround(project(":krpc-shared-api"))
    testImplementationWorkaround(project(":krpc-shared-impl"))
    testImplementationWorkaround(project(":krpc-server-api"))
    testImplementationWorkaround(project(":krpc-server-impl"))
    testImplementationWorkaround(project(":krpc-client-api"))
    testImplementationWorkaround(project(":krpc-client-impl"))
    testImplementationWorkaround(project(":krpc-annotations"))
    testImplementationWorkaround(project(":krpc-test"))

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

fun DependencyHandlerScope.implementationWorkaround(dependency: ProjectDependency) {
    implementation(dependency)
    val project = dependency.dependencyProject
    compileOnly(
        files(File(project.buildDir, "libs/${project.name}-jvm-${project.version}.jar"))
    )
}

fun DependencyHandlerScope.testImplementationWorkaround(dependency: ProjectDependency) {
    testImplementation(dependency)
    val project = dependency.dependencyProject
    testCompileOnly(
        files(File(project.buildDir, "libs/${project.name}-jvm-${project.version}.jar"))
    )
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
