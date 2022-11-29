package org.brightify.hyperdrive

import org.brightify.hyperdrive.debug.DebugCommandLineProcessor
import org.brightify.hyperdrive.krpc.plugin.KrpcCommandLineProcessor
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.exclude
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.the
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.FilesSubpluginOption
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

class HyperdriveGradlePlugin: Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create<HyperdriveExtension>(EXTENSION_NAME)
        project.apply<KrpcGradleSubplugin>()
        project.apply<MultiplatformXGradleSubplugin>()
        project.apply<DebugGradleSubplugin>()

        with(project) {
            val kotlinPluginTransitivityWorkaround = configurations.maybeCreate(KOTLIN_PLUGIN_TRANSITIVITY_WORKAROUND_CONFIGURATION).apply {
                isCanBeResolved = true
                isCanBeConsumed = false
                isVisible = false

                exclude("org.jetbrains.kotlin")

                attributes {
                    it.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.LIBRARY))
                    it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_RUNTIME))
                    it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements.JAR))
                }
            }

            dependencies {
                kotlinPluginTransitivityWorkaround(
                    group = BuildConfig.KOTLIN_PLUGIN_GROUP,
                    name = BuildConfig.KOTLIN_PLUGIN_NAME,
                    version = BuildConfig.KOTLIN_PLUGIN_VERSION,
                )
            }

            afterEvaluate {
                val pluginDependencies = project.configurations.named(KOTLIN_PLUGIN_TRANSITIVITY_WORKAROUND_CONFIGURATION).map { configuration ->
                    configuration.resolvedConfiguration.firstLevelModuleDependencies
                        .flatMap { it.children }
                        .flatMap { it.allModuleArtifacts }
                        .map { it.file }
                        .toSet()
                }
                project.the<KotlinMultiplatformExtension>().targets {
                    this.all { target ->
                        if (target !is KotlinNativeTarget) {
                            return@all
                        }

                        target.compilations.forEach { compilation ->
                            compilation.compileKotlinTaskProvider.configure {
                                it.kotlinOptions.freeCompilerArgs += pluginDependencies.get().map {
                                    "-Xplugin=${it.absolutePath}"
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTENSION_NAME = "hyperdrive"
        const val KOTLIN_PLUGIN_TRANSITIVITY_WORKAROUND_CONFIGURATION = "kotlinPluginTransitivityWorkaround"
    }
}
