@file:Suppress("UNUSED")

package org.brightify.hyperdrive

import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.getting
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.getValue
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

fun KotlinMultiplatformExtension.configurePlatforms() {
    jvm()
    ios()
    // iosSimulatorArm64()
    tvos()
    // tvosSimulatorArm64()
    macosX64()
    // macosArm64()
    js {
        browser {
            testTask {
                enabled = false
            }
        }
        nodejs()
    }

    sourceSets.apply {
        val commonMain by getting
        val commonTest by getting
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val nativeTest by creating {
            dependsOn(commonTest)
        }

        val iosMain by getting {
            dependsOn(nativeMain)
        }
        val iosTest by getting {
            dependsOn(nativeTest)
        }

        // val iosSimulatorArm64Main by getting {
        //     dependsOn(iosMain)
        // }
        // val iosSimulatorArm64Test by getting {
        //     dependsOn(iosTest)
        // }

        val tvosMain by getting {
            dependsOn(nativeMain)
        }
        val tvosTest by getting {
            dependsOn(nativeTest)
        }
        // val tvosSimulatorArm64Main by getting {
        //     dependsOn(tvosMain)
        // }
        // val tvosSimulatorArm64Test by getting {
        //     dependsOn(tvosTest)
        // }

        val macosMain by creating {
            dependsOn(nativeMain)
        }
        val macosTest by creating {
            dependsOn(nativeTest)
        }
        val macosX64Main by getting {
            dependsOn(macosMain)
        }
        val macosX64Test by getting {
            dependsOn(macosTest)
        }
        // val macosArm64Main by getting {
        //     dependsOn(macosMain)
        // }
        // val macosArm64Test by getting {
        //     dependsOn(macosTest)
        // }
    }
}