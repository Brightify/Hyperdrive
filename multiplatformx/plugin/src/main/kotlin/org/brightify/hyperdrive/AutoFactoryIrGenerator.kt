package org.brightify.hyperdrive

import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.irConstructorCall
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

open class AutoFactoryIrGenerator(
    private val compilerContext: IrPluginContext
): IrElementTransformerVoid(), ClassLoweringPass {
    @OptIn(ObsoleteDescriptorBasedAPI::class)
    override fun lower(irClass: IrClass) {
        if (irClass.name != AutoFactoryNames.factory) { return }

        val autoFactoryConstructor = irClass.parentAutoFactoryConstructor ?: return

        val factoryConstructor = irClass.constructors.single { it.visibility == DescriptorVisibilities.PUBLIC }
        val factoryCreateMethod = irClass.functions.single { it.name == AutoFactoryNames.createFun }

        val newParametersAccess: List<Either<IrValueParameter, Pair<IrField, IrValueParameter>>> = autoFactoryConstructor.valueParameters.map { parameter ->
            if (parameter.hasAnnotation(AutoFactoryNames.Annotation.provided)) {
                val newParameter = factoryCreateMethod.valueParameters.single { it.name == parameter.name }
                Either.Left<IrValueParameter, Pair<IrField, IrValueParameter>>(newParameter)
            } else {
                val field = irClass.addField {
                    name = parameter.name
                    type = parameter.type
                    visibility = DescriptorVisibilities.PRIVATE
                }
                val constructorParameter = factoryConstructor.valueParameters.single { it.name == parameter.name }
                Either.Right<IrValueParameter, Pair<IrField, IrValueParameter>>(field to constructorParameter)
            }
        }

        factoryConstructor.body = DeclarationIrBuilder(compilerContext, factoryConstructor.symbol).irBlockBody {
            +irDelegatingConstructorCall(compilerContext.symbols.any.constructors.first().owner)

            for (newParameterAccess in newParametersAccess) {
                if (newParameterAccess is Either.Right) {
                    +irSetField(irGet(irClass.thisReceiver!!), newParameterAccess.value.first, irGet(newParameterAccess.value.second))
                }
            }
        }

        factoryCreateMethod.body = DeclarationIrBuilder(compilerContext, factoryCreateMethod.symbol).irBlockBody {
            +irReturn(
                irConstructorCall(
                    irCall(autoFactoryConstructor).also { call ->
                        for ((index, newParameterAccess) in newParametersAccess.withIndex()) {
                            when (newParameterAccess) {
                                is Either.Left -> {
                                    call.putValueArgument(index, irGet(newParameterAccess.value))
                                }
                                is Either.Right -> {
                                    call.putValueArgument(index, irGetField(irGet(factoryCreateMethod.dispatchReceiverParameter!!), newParameterAccess.value.first))
                                }
                            }
                        }
                    },
                    autoFactoryConstructor.symbol
                )
            )
        }
    }
}