import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper

group = "org.brightify.hyperdrive"
version = System.getenv("RELEASE_VERSION") ?: "1.0.0-SNAPSHOT"

plugins.withType<KotlinPluginWrapper> {
    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(17)
    }
}

plugins.withType<KotlinMultiplatformPluginWrapper> {
    extensions.configure<KotlinMultiplatformExtension> {
        jvmToolchain(17)
    }
}
