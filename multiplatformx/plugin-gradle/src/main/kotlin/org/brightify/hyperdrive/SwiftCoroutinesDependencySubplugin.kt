package org.brightify.hyperdrive

import org.brightify.hyperdrive.swiftbridge.KLibraryMetadataReaderWriter
import org.brightify.hyperdrive.swiftbridge.Container
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.container
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinNativeBinaryContainer
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.AbstractNativeLibrary

class SwiftCoroutinesDependencySubplugin: Plugin<Project> {
    override fun apply(target: Project) {
        println(target)

//        target.tasks.register("generateSwiftBridge", GenerateSwiftBridgeTask::class.java)

        target.afterEvaluate {
            println(it)
        }
    }
}

open class CopyResourcesToKLibrary: DefaultTask() {
    @TaskAction
    fun copyResources() {
        project.configurations.getByName("archives").artifacts.files.toList()
    }
}

open class GenerateSwiftBridgeTask: DefaultTask() {

    @TaskAction
    fun generateSwiftBridge() {
        val dependencies = project.configurations.flatMap { it.dependencies }.toSet()
        val container = project.container<KotlinNativeBinaryContainer>()

        val binaries = project.extensions.getByType<KotlinMultiplatformExtension>().targets.flatMap {
            if (it is KotlinNativeTarget) {
                it.binaries.toSet()
            } else {
                emptySet()
            }
        }
        val exportConfigurations = binaries.mapNotNull {
            if (it is AbstractNativeLibrary) {
                it.exportConfigurationName
            } else {
                null
            }
        }.toSet()

        val exportKLibraries = exportConfigurations.flatMap {
            project.configurations.getByName(it).files
        }.toSet()

        val containers: Set<Container> = exportKLibraries.flatMap {
            KLibraryMetadataReaderWriter.read(it)?.containers ?: emptyList()
        }.toSet()

        println(exportKLibraries)
    }
}