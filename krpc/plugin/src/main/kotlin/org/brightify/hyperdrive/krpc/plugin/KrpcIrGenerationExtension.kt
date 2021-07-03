package org.brightify.hyperdrive.krpc.plugin

import org.brightify.hyperdrive.krpc.plugin.ir.lower.KrpcClientLowering
import org.brightify.hyperdrive.krpc.plugin.ir.lower.KrpcDebugPrintingLowering
import org.brightify.hyperdrive.krpc.plugin.ir.lower.KrpcDescriptorCallLowering
import org.brightify.hyperdrive.krpc.plugin.ir.lower.KrpcDescriptorLowering
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.runOnFilePostfix
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

open class KrpcIrGenerationExtension(
    val printIR: Boolean = false,
    val printKotlinLike: Boolean = false,
    val messageCollector: MessageCollector = MessageCollector.NONE,
): IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val transformers = listOf(
            KrpcClientLowering(pluginContext, messageCollector),
            KrpcDescriptorLowering(pluginContext, messageCollector),
            KrpcDescriptorCallLowering(pluginContext, messageCollector),
            KrpcDebugPrintingLowering(printIR = printIR, printKotlinLike = printKotlinLike),
        )

        for (file in moduleFragment.files) {
            for (transformer in transformers) {
                transformer.runOnFilePostfix(file)
            }
        }
    }
}
