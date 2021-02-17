plugins {
    kotlin("multiplatform")
}

kotlin {
    explicitApi()

    jvm()
    ios()
    tvos()
    macosX64()
    watchosArm32()
    watchosArm64()
    watchosX86()
    js {
        browser()
        nodejs()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
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

        val watchosMain by creating {
            dependsOn(nativeMain)
        }
        val watchosArm32Main by getting {
            dependsOn(watchosMain)
        }
        val watchosArm64Main by getting {
            dependsOn(watchosMain)
        }
        val watchosX86Main by getting {
            dependsOn(watchosMain)
        }
    }
}