plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()
    ios()
    tvos()
    macosX64()
    js {
        browser()
        nodejs()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":krpc-shared-impl"))
                implementation(project(":logging-api"))
                implementation(project(":kotlin-utils"))

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core")
                implementation("io.ktor:ktor-client-websockets")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(project(":krpc-test"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit5"))

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
                implementation("org.junit.jupiter:junit-jupiter")

                implementation("io.kotest:kotest-runner-junit5")
                implementation("io.kotest:kotest-assertions-core")
                implementation("io.kotest:kotest-property")
            }
        }
    }

    sourceSets {
        all {
            languageSettings.useExperimentalAnnotation("kotlinx.serialization.ExperimentalSerializationApi")
        }
    }
}
