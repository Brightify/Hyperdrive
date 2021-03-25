package org.brightify.hyperdrive.krpc.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.runOnFilePostfix
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class KrpcIrGenerationExtension(
    val printIR: Boolean = false,
    val printKotlinLike: Boolean = false,
    val messageCollector: MessageCollector = MessageCollector.NONE,
): IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val generator = KrpcIrGenerator(pluginContext, messageCollector, printIR = printIR, printKotlinLike = printKotlinLike)
        for (file in moduleFragment.files) {
            generator.runOnFilePostfix(file)
        }
    }
}

