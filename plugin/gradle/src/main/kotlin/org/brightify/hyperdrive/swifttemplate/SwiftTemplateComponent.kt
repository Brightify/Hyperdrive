package org.brightify.hyperdrive.swifttemplate

import org.gradle.api.component.SoftwareComponent
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.util.PatternSet
import java.util.concurrent.Callable

class SwiftTemplateComponent(
    private val name: String,
    objectFactory: ObjectFactory,
    private val projectLayout: ProjectLayout,
): SoftwareComponent {
    private val defaultSource = objectFactory.fileCollection().apply {
        setFrom(
            listOf(
                projectLayout.projectDirectory.dir("src/$name/swift"),
                projectLayout.buildDirectory.dir("generated/$name/swift"),
            )
        )
    }
    val source: ConfigurableFileCollection = objectFactory.fileCollection()
    val swiftTemplateSource = createSourceView("swift.template", "swift")

    fun source(action: ConfigurableFileCollection.() -> Unit) {
        source.action()
    }

    override fun getName(): String {
        return name
    }

    private fun createSourceView(vararg sourceExtensions: String): FileCollection {
        val patternSet = PatternSet()
        for (sourceExtension in sourceExtensions) {
            patternSet.include("**/*.$sourceExtension")
        }
        return projectLayout.files(object: Callable<Any> {
            override fun call(): Any {
                return if (source.from.isEmpty()) {
                    defaultSource.asFileTree
                } else {
                    source.asFileTree
                }.matching(patternSet)
            }
        })
    }
}