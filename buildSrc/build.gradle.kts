buildscript {
    repositories {
        mavenCentral()
        jcenter()
    }

    dependencies {
        classpath(kotlin("gradle-plugin", version = "1.4.21"))
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
    kotlin("jvm") version "1.4.21"
    kotlin("kapt") version "1.4.21"
    `java-gradle-plugin`
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:1.4.21")
    implementation(kotlin("gradle-plugin", version = "1.4.21"))

    implementation("com.google.devtools.ksp:symbol-processing:1.4.20-dev-experimental-20210203")
//    implementation("org.jetbrains.kotlin:kotlin-ksp:1.4.0-dev-experimental-20200828:sources")
//    implementation("org.jetbrains.kotlin:kotlin-ksp:1.4.0-dev-experimental-20200828:javadoc")

    compileOnly("com.google.auto.service:auto-service:1.0-rc6")
    kapt("com.google.auto.service:auto-service:1.0-rc6")
}
