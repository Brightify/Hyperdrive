package org.brightify.hyperdrive

import org.brightify.hyperdrive.swifttemplate.SwiftTemplateSubplugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create

class HyperdriveGradlePlugin: Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        val extension = extensions.create<HyperdriveExtension>(EXTENSION_NAME)
        apply<KrpcGradleSubplugin>()
        apply<MultiplatformXGradleSubplugin>()
        apply<SwiftTemplateSubplugin>()
    }

    companion object {
        const val EXTENSION_NAME = "hyperdrive"
    }
}