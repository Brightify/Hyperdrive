plugins {
    kotlin("multiplatform")
    // Include in documentation generation.
    id("org.jetbrains.dokka")
}

kotlin {
    explicitApi()

    jvm()
    ios()
    tvos()
    macosX64()
    js {
        browser {
            testTask {
                enabled = false
            }
        }
        nodejs()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":kotlin-utils"))
                api(project(":logging-api"))
                implementation(libs.stately.common)
                implementation(libs.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.coroutines.core)
                implementation(kotlin("reflect"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.junit.jupiter)
                implementation(libs.coroutines.test)
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

        val nativeMain by creating {
            dependsOn(commonMain)
        }

        val iosMain by getting {
            dependsOn(nativeMain)
        }

        val macosX64Main by getting {
            dependsOn(nativeMain)
        }

        val tvosMain by getting {
            dependsOn(nativeMain)
        }
    }
}

tasks.dokkaHtmlPartial.configure {
    moduleName.set("Singularity API (${moduleName.get()})")
}
