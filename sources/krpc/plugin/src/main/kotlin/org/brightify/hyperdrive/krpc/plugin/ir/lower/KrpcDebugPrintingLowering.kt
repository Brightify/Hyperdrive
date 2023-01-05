package org.brightify.hyperdrive.krpc.plugin.ir.lower

import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

class KrpcDebugPrintingLowering(
    private val printIR: Boolean,
    private val printKotlinLike: Boolean,
): IrElementTransformerVoid(), ClassLoweringPass {
    override fun lower(irClass: IrClass) {
        if (printIR || printKotlinLike) {
            println("================== BEGIN <${irClass.name.asString()}> ==================")
            if (printKotlinLike) {
                try {
                    println(irClass.dumpKotlinLike())
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
            if (printIR && printKotlinLike) {
                println("==================")
            }
            if (printIR) {
                try {
                    println(irClass.dump())
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
            println("================== END <${irClass.name.asString()}> ==================")
        }
    }
}