package org.brightify.hyperdrive.viewmodel

import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.classIfConstructor
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irBlockBody
import org.jetbrains.kotlin.backend.common.serialization.proto.IrPropertyReferenceOrBuilder
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.ir.builders.declarations.buildProperty
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.parent
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.expressions.IrPropertyReference
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrPropertyReferenceImpl
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.KotlinTypeFactory
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.typeUtil.createProjection

class ViewModelIrGenerator(
    private val pluginContext: IrPluginContext
): IrElementTransformerVoid(), ClassLoweringPass {
    override fun lower(irClass: IrClass) {
        if (!irClass.hasAnnotation(ViewModelNames.Annotation.viewModel)) { return }

        val lazy = pluginContext.referenceClass(ViewModelNames.Kotlin.lazy) ?: return
        val lazyValue = lazy.getPropertyGetter(ViewModelNames.Kotlin.Lazy.value.identifier) ?: return
        val observe = irClass.functions.singleOrNull { it.name == Name.identifier("observe") } ?: return

        for (property in irClass.properties) {
            if (!property.name.identifier.startsWith("observe")) { continue }
            val propertyGetter = property.getter ?: continue
            if (propertyGetter.body != null) { continue }

            val referencedProperty = irClass.properties.singleOrNull { property.name.identifier == "observe${it.name.identifier.capitalize()}" } ?: continue

            propertyGetter.body = DeclarationIrBuilder(pluginContext, propertyGetter.symbol).irBlockBody {
                +irReturn(
                    irCall(lazyValue, propertyGetter.returnType).apply {
                        dispatchReceiver = irCall(observe.symbol, lazy.typeWith(propertyGetter.returnType)).apply {
                            putTypeArgument(0, referencedProperty.getter!!.returnType)
                            dispatchReceiver = propertyGetter.dispatchReceiverParameter?.let { irGet(it) }
                            putValueArgument(0,
                                IrPropertyReferenceImpl(
                                    referencedProperty.startOffset,
                                    referencedProperty.endOffset,
                                    pluginContext.symbols.kproperty0().typeWith(referencedProperty.getter!!.returnType),
                                    referencedProperty.symbol,
                                    0,
                                    null,
                                    referencedProperty.getter?.symbol,
                                    referencedProperty.setter?.symbol,
                                    null
                                ).apply {
                                    dispatchReceiver = propertyGetter.dispatchReceiverParameter?.let { irGet(it) }
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}