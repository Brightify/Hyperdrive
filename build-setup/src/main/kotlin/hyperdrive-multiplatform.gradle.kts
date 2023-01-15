plugins {
    kotlin("multiplatform")
}

kotlin {
    explicitApi()

    jvm()
    ios()
    iosSimulatorArm64()
    tvos()
    tvosSimulatorArm64()

    macosX64()
    macosArm64()

    js(IR) {
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
                implementation(kotlin("stdlib"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
            }
        }

        val darwinMain by creating {
            dependsOn(commonMain)
        }
        val darwinTest by creating {
            dependsOn(commonTest)
        }

        val iosMain by getting {
            dependsOn(darwinMain)
        }
        val iosTest by getting {
            dependsOn(darwinTest)
        }
        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }
        val iosSimulatorArm64Test by getting {
            dependsOn(iosTest)
        }

        val tvosMain by getting {
            dependsOn(darwinMain)
        }
        val tvosTest by getting {
            dependsOn(darwinTest)
        }
        val tvosSimulatorArm64Main by getting {
            dependsOn(tvosMain)
        }
        val tvosSimulatorArm64Test by getting {
            dependsOn(tvosTest)
        }

        val macosMain by creating {
            dependsOn(darwinMain)
        }
        val macosTest by creating {
            dependsOn(darwinTest)
        }
        val macosX64Main by getting {
            dependsOn(macosMain)
        }
        val macosX64Test by getting {
            dependsOn(macosTest)
        }
        val macosArm64Main by getting {
            dependsOn(macosMain)
        }
        val macosArm64Test by getting {
            dependsOn(macosTest)
        }
    }
}
