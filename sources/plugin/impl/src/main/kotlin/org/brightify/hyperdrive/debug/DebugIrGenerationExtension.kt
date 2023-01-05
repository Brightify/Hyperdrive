package org.brightify.hyperdrive.debug

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.FqName

open class DebugIrGenerationExtension(
    val disablePrintIR: Boolean = false,
    val disablePrintKotlinLike: Boolean = false,
    val messageCollector: MessageCollector = MessageCollector.NONE,
): IrGenerationExtension {
    companion object {
        val debugIrAnnotation = FqName("org.brightify.hyperdrive.debug.DebugIR")
    }
    private val visitor = object: IrElementVisitorVoid {
        override fun visitElement(element: IrElement) {
            val debugIrAnnotation = (element as? IrAnnotationContainer)?.getAnnotation(debugIrAnnotation) ?: return run {
                // We don't run for children if there's a @DebugIR on a parent to limit the amount of duplicate prints.
                element.acceptChildrenVoid(this)
            }
            // TODO: Use the @DebugIR arguments to control the dumps.
            if (!disablePrintIR) {
                messageCollector.report(CompilerMessageSeverity.INFO, element.dump())
            }
            if (disablePrintKotlinLike) {
                messageCollector.report(CompilerMessageSeverity.INFO, element.dumpKotlinLike())
            }
        }
    }

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        for (file in moduleFragment.files) {
            visitor.visitFile(file)
        }
    }
}
