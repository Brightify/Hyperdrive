package org.brightify.hyperdrive

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create

class HyperdriveGradlePlugin: Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create<HyperdriveExtension>(EXTENSION_NAME)
        project.apply<KrpcGradleSubplugin>()
        project.apply<MultiplatformXGradleSubplugin>()
        project.apply<DebugGradleSubplugin>()
    }

    companion object {
        const val EXTENSION_NAME = "hyperdrive"
    }
}