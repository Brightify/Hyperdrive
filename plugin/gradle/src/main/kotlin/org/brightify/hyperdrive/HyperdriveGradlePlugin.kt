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
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class HyperdriveGradlePlugin: Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create<HyperdriveExtension>(EXTENSION_NAME)
        project.apply<HyperdriveGradleKotlinSubplugin>()
    }

    companion object {
        const val EXTENSION_NAME = "hyperdrive"
    }
}


class HyperdriveGradleKotlinSubplugin: KotlinCompilerPluginSupportPlugin {
    private companion object {
        const val kotlinPluginTransitivityWorkaround = "kotlinPluginTransitivityWorkaround"
    }

    override fun apply(target: Project) {
        super.apply(target)

        with(target) {

            val kotlinPluginTransitivityWorkaround = configurations.maybeCreate(kotlinPluginTransitivityWorkaround).apply {
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
                val pluginArtifact = getPluginArtifact()
                kotlinPluginTransitivityWorkaround(
                    group = pluginArtifact.groupId,
                    name = pluginArtifact.groupId,
                    version = pluginArtifact.version,
                )
            }
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    // TODO: Apply required dependencies?
    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project

        val pluginDependencies = project.configurations.getByName(kotlinPluginTransitivityWorkaround).resolve()
            .toSet()
            .joinToString(", ") { "\"${it.absolutePath}\"" }
        kotlinCompilation.kotlinOptions.freeCompilerArgs += pluginDependencies.map {
            "-Xplugin=${it}"
        }

        return project.provider {
            val hyperdrive = project.extensions.findByType<HyperdriveExtension>() ?: HyperdriveExtension()

            hyperdrive.debugOptions() + hyperdrive.krpcOptions() + hyperdrive.multiplatformxOptions()
        }
    }

    override fun getCompilerPluginId() = KrpcCommandLineProcessor.pluginId

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(
            groupId = BuildConfig.KOTLIN_PLUGIN_GROUP,
            artifactId = BuildConfig.KOTLIN_PLUGIN_NAME,
            version = BuildConfig.KOTLIN_PLUGIN_VERSION,
        )

    private fun HyperdriveExtension.debugOptions(): List<SubpluginOption> = debug?.let { debug ->
        listOf(
            DebugCommandLineProcessor.Options.enabled.subpluginOption("true"),
            DebugCommandLineProcessor.Options.disablePrintIR.subpluginOption(debug.disablePrintIR.toString()),
            DebugCommandLineProcessor.Options.disablePrintKotlinLike.subpluginOption(debug.disablePrintKotlinLike.toString())
        )
    } ?: listOf(
        DebugCommandLineProcessor.Options.enabled.subpluginOption("false")
    )

    private fun HyperdriveExtension.krpcOptions(): List<SubpluginOption> = krpc?.let { krpc ->
        listOf(
            KrpcCommandLineProcessor.Options.enabled.subpluginOption("true"),
            KrpcCommandLineProcessor.Options.printIR.subpluginOption(krpc.printIR.toString()),
            KrpcCommandLineProcessor.Options.printKotlinLike.subpluginOption(krpc.printKotlinLike.toString())
        )
    } ?: listOf(
        KrpcCommandLineProcessor.Options.enabled.subpluginOption("false")
    )

    private fun HyperdriveExtension.multiplatformxOptions(): List<SubpluginOption> = multiplatformx?.let { multiplatformx ->
        listOf(
            MultiplatformxCommandLineProcessor.Options.enabled.subpluginOption("true"),
            MultiplatformxCommandLineProcessor.Options.autoFactoryEnabled.subpluginOption(multiplatformx.isAutoFactoryEnabled),
            MultiplatformxCommandLineProcessor.Options.viewModelEnabled.subpluginOption(multiplatformx.isViewModelEnabled),
            MultiplatformxCommandLineProcessor.Options.viewModelAutoObserveEnabled.subpluginOption(multiplatformx.isComposableAutoObserveEnabled),
        )
    } ?: listOf(
        MultiplatformxCommandLineProcessor.Options.enabled.subpluginOption("false")
    )
}
