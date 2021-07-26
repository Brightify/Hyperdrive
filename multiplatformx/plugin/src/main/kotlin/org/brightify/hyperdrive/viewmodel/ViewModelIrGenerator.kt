package org.brightify.hyperdrive.viewmodel

import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.classIfConstructor
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irBlockBody
import org.jetbrains.kotlin.backend.common.serialization.proto.IrPropertyReferenceOrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.declarations.IrFieldBuilder
import org.jetbrains.kotlin.ir.builders.declarations.buildProperty
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.parent
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrFieldImpl
import org.jetbrains.kotlin.ir.declarations.lazy.IrLazyField
import org.jetbrains.kotlin.ir.expressions.IrPropertyReference
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrPropertyReferenceImpl
import org.jetbrains.kotlin.ir.symbols.IrDelegatingPropertySymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrFieldSymbolImpl
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.KotlinTypeFactory
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.typeUtil.createProjection
import java.util.*

class ViewModelIrGenerator(
    private val pluginContext: IrPluginContext
): IrElementTransformerVoid(), ClassLoweringPass {
    @OptIn(ObsoleteDescriptorBasedAPI::class)
    override fun lower(irClass: IrClass) {
        if (!irClass.hasAnnotation(ViewModelNames.Annotation.viewModel)) { return }

        val lazy = pluginContext.referenceClass(ViewModelNames.Kotlin.lazy) ?: return
        val lazyValue = lazy.getPropertyGetter(ViewModelNames.Kotlin.Lazy.value.identifier) ?: return
        val observe = irClass.functions.singleOrNull { it.name == Name.identifier("observe") } ?: return

        for (property in irClass.properties) {
            val referencedPropertyName = NamingHelper.getReferencedPropertyName(property.name.identifier) ?: continue

            val propertyGetter = property.getter ?: continue
            if (propertyGetter.body != null) { continue }

            val referencedProperty = irClass.properties.singleOrNull { classProperty ->
                classProperty.name.identifier == referencedPropertyName
            } ?: continue

            val declarationBuilder = DeclarationIrBuilder(pluginContext, propertyGetter.symbol)
            val delegateField = IrFieldImpl(
                startOffset = property.startOffset,
                endOffset = property.endOffset,
                origin = IrDeclarationOrigin.PROPERTY_DELEGATE,
                symbol = IrFieldSymbolImpl(property.descriptor),
                name = Name.identifier("${property.name.identifier}\$delegate"),
                type = lazy.typeWith(propertyGetter.returnType),
                visibility = DescriptorVisibilities.PRIVATE,
                isFinal = true,
                isExternal = false,
                isStatic = false,
            ).also { field ->
                field.parent = irClass
                field.initializer = declarationBuilder.irExprBody(
                    declarationBuilder.irCall(observe.symbol, field.type).apply {
                        putTypeArgument(0, referencedProperty.getter!!.returnType)
                        dispatchReceiver = irClass.thisReceiver?.let { declarationBuilder.irGet(it) }
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
                                dispatchReceiver = irClass.thisReceiver?.let { declarationBuilder.irGet(it) }
                            }
                        )
                    }
                )
            }
            property.backingField = delegateField
            propertyGetter.body = declarationBuilder.irBlockBody {
                +irReturn(
                    irCall(lazyValue, propertyGetter.returnType).apply {
                        dispatchReceiver = irGetField(propertyGetter.dispatchReceiverParameter?.let { irGet(it) }, delegateField)
                    }
                )
            }
        }
    }
}