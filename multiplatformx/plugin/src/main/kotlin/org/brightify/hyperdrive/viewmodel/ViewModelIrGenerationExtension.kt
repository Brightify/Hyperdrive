package org.brightify.hyperdrive.viewmodel

import org.brightify.hyperdrive.autofactory.AutoFactoryIrGenerator
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.runOnFilePostfix
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

open class ViewModelIrGenerationExtension: IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val generator = ViewModelIrGenerator(pluginContext)
        for (file in moduleFragment.files) {
            generator.runOnFilePostfix(file)
        }
    }
}