package org.brightify.hyperdrive.autofactory

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.runOnFilePostfix
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

@AutoService(IrGenerationExtension::class)
open class AutoFactoryIrGenerationExtension: IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val generator = AutoFactoryIrGenerator(pluginContext)
        for (file in moduleFragment.files) {
            generator.runOnFilePostfix(file)
        }
    }
}