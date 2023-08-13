import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.`maven-publish`

plugins {
    id("hyperdrive-base")

    id("com.android.library")
    kotlin("android")
    id("hyperdrive-publishable")
}
