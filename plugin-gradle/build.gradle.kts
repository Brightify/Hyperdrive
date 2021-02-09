import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    `java-gradle-plugin`
    id("com.github.gmazzo.buildconfig") version Versions.buildConfig
}

buildConfig {
    packageName(project.group.toString())
    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"org.brightify.hyperdrive\"")
    buildConfigField("String", "KOTLIN_PLUGIN_GROUP", "\"${project.group}\"")
    buildConfigField("String", "KOTLIN_PLUGIN_VERSION", "\"${project.version}\"")
    buildConfigField("String", "KOTLIN_PLUGIN_NAME", "\"${project(":plugin").name}\"")
    buildConfigField("String", "KOTLIN_NATIVE_PLUGIN_NAME", "\"${project(":plugin-native").name}\"")
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

gradlePlugin {
    plugins {
        create("hyperdrive") {
            id = "org.brightify.hyperdrive"
            implementationClass = "org.brightify.hyperdrive.HyperdriveGradlePlugin"
        }

        create("symbol-processing") {
            id = "org.brightify.hyperdrive.symbol-processing"
            implementationClass = "org.brightify.hyperdrive.KspGradleSubplugin"
        }
    }
}

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin-api:${Versions.kotlin}")
    compileOnly(kotlin("gradle-plugin", version = Versions.kotlin))
    implementation("com.google.devtools.ksp:symbol-processing:${Versions.symbolProcessing}")

    compileOnly(gradleApi())
    compileOnly(gradleKotlinDsl())

    compileOnly("com.google.auto.service:auto-service:${Versions.autoService}")
    kapt("com.google.auto.service:auto-service:${Versions.autoService}")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.3.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}