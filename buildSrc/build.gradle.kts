buildscript {
    repositories {
        mavenCentral()
        jcenter()
    }

    dependencies {
        classpath(kotlin("gradle-plugin", version = "1.4.20"))
    }

    allprojects {
        repositories {
            mavenCentral()
            mavenLocal()
            jcenter()
            google()
        }
    }
}

plugins {
    kotlin("jvm") version "1.4.0"
    kotlin("kapt") version "1.4.0"
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("simplePlugin") {
            id = "org.brightify.hyperdrive"
            implementationClass = "com.google.devtools.ksp.gradle.KspGradleSubplugin"
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:1.4.0")
    implementation(kotlin("gradle-plugin", version = "1.4.0"))

    implementation("com.google.devtools.ksp:symbol-processing:1.4.20-dev-experimental-20201222")
//    implementation("org.jetbrains.kotlin:kotlin-ksp:1.4.0-dev-experimental-20200828:sources")
//    implementation("org.jetbrains.kotlin:kotlin-ksp:1.4.0-dev-experimental-20200828:javadoc")

    compileOnly("com.google.auto.service:auto-service:1.0-rc6")
    kapt("com.google.auto.service:auto-service:1.0-rc6")
}
