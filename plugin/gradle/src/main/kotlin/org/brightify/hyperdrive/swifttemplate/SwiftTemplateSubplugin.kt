package org.brightify.hyperdrive.swifttemplate

import org.brightify.hyperdrive.HyperdriveExtension
import org.gradle.api.Named
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.model.ObjectFactory
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.expand
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File
import javax.inject.Inject

class SwiftTemplateSubplugin @Inject constructor(
    private val objectFactory: ObjectFactory,
): Plugin<Project> {
    inline fun <reified T: Named> Project.namedAttribute(value: String) = objects.named(T::class, value)

    override fun apply(project: Project): Unit = with(project) {
        val extension = extensions.getByType<HyperdriveExtension>()
        val swiftTemplateComponent = SwiftTemplateComponent("main", objectFactory, project.layout)
        extension.swiftTemplate = swiftTemplateComponent
        components.add(swiftTemplateComponent)

        val swiftTemplateConfiguration = configurations.register("swiftTemplate") {
            it.isCanBeConsumed = false
            it.isCanBeResolved = true

            it.attributes {
                it.attribute(Category.CATEGORY_ATTRIBUTE, namedAttribute(Category.LIBRARY))
                it.attribute(Usage.USAGE_ATTRIBUTE, namedAttribute(Usage.SWIFT_API))
                it.attribute(Bundling.BUNDLING_ATTRIBUTE, namedAttribute(Bundling.EXTERNAL))
                it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, namedAttribute("swift-templates"))
            }
        }

        val packSwiftTemplates = tasks.create("packSwiftTemplates", Zip::class.java) {
            it.archiveAppendix.set("swiftTemplates")
            it.destinationDirectory.set(layout.buildDirectory.dir("libs"))
            it.from(swiftTemplateComponent.swiftTemplateSource)
        }

        val outgoingSwiftTemplateConfiguration = configurations.register("outgoingSwiftTemplate") {
            it.isCanBeConsumed = true
            it.isCanBeResolved = false

            it.attributes {
                it.attribute(Category.CATEGORY_ATTRIBUTE, namedAttribute(Category.LIBRARY))
                it.attribute(Usage.USAGE_ATTRIBUTE, namedAttribute(Usage.SWIFT_API))
                it.attribute(Bundling.BUNDLING_ATTRIBUTE, namedAttribute(Bundling.EXTERNAL))
                it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, namedAttribute("swift-templates"))
            }

            it.outgoing.artifact(packSwiftTemplates) {
                it.builtBy(packSwiftTemplates)
            }
        }

        afterEvaluate {
            extensions.findByType<KotlinMultiplatformExtension>()?.apply {
                val nativeTargets = targets.withType<KotlinNativeTarget>()
                if (nativeTargets.any { it.binaries.withType<Framework>().isNotEmpty() }) {
                    for (target in nativeTargets) {
                        for (compilation in target.compilations) {
                            if (!compilation.name.toLowerCase().contains("test")) {
                                swiftTemplateConfiguration.configure {
                                    it.extendsFrom(
                                        configurations.getByName(compilation.apiConfigurationName),
                                        configurations.getByName(compilation.implementationConfigurationName),
                                    )
                                }
                            }
                        }
                    }

                    println(nativeTargets.map { it.artifactsTaskName })
                    val copySwiftTemplates = tasks.create("copySwiftTemplates") {
                        it.dependsOn(swiftTemplateConfiguration)

                        it.doLast {
                            val swiftTemplateOutputRoot = File(buildDir, "outputs/swiftTemplate")
                            val binariesByBaseNames = targets.withType<KotlinNativeTarget>()
                                .flatMap { it.binaries.withType<Framework>() }
                                .groupBy { it.baseName }

                            val templateArchives = swiftTemplateConfiguration.get().incoming.artifactView { it.isLenient = true }.files

                            for ((baseName, binaries) in binariesByBaseNames) {
                                val swiftTemplateOutput = File(swiftTemplateOutputRoot, baseName)
                                swiftTemplateOutput.mkdirs()

                                for (templateArchive in templateArchives) {
                                    copy {
                                        it.from(zipTree(templateArchive))
                                        it.into(swiftTemplateOutput)
                                        it.rename { name ->
                                            if (name.endsWith(".swift.template")) {
                                                name.dropLast(".template".length)
                                            } else {
                                                name
                                            }
                                        }
                                        it.expand("baseName" to baseName)
                                    }
                                }
                            }
                        }
                    }

                    for (target in nativeTargets) {
                        for (compilation in target.compilations) {
                            copySwiftTemplates.dependsOn(compilation.compileKotlinTask)
                            tasks.getByName(compilation.binariesTaskName).dependsOn(copySwiftTemplates)
                        }

                        tasks.getByName(target.artifactsTaskName).dependsOn(copySwiftTemplates)
                    }
                }
            }

            extensions.findByType<PublishingExtension>()?.apply {
                publications.apply {
                    create<MavenPublication>("swiftsources") {
                        from(swiftTemplateComponent)
                    }
                }
            }
        }
    }
}