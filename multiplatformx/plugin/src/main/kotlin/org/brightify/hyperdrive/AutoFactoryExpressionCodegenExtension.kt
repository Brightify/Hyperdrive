package org.brightify.hyperdrive

import org.jetbrains.kotlin.codegen.ImplementationBodyCodegen
import org.jetbrains.kotlin.codegen.StackValue
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

class AutoFactoryExpressionCodegenExtension: ExpressionCodegenExtension {
    override val shouldGenerateClassSyntheticPartsInLightClassesMode: Boolean
        get() = super.shouldGenerateClassSyntheticPartsInLightClassesMode

    override fun applyFunction(receiver: StackValue, resolvedCall: ResolvedCall<*>, c: ExpressionCodegenExtension.Context): StackValue? {
        return super.applyFunction(receiver, resolvedCall, c)
    }

    override fun applyProperty(receiver: StackValue, resolvedCall: ResolvedCall<*>, c: ExpressionCodegenExtension.Context): StackValue? {
        return super.applyProperty(receiver, resolvedCall, c)
    }

    override fun generateClassSyntheticParts(codegen: ImplementationBodyCodegen) {
        super.generateClassSyntheticParts(codegen)
    }
}