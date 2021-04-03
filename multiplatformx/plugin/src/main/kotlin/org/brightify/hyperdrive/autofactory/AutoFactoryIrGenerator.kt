package org.brightify.hyperdrive.autofactory

import org.brightify.hyperdrive.Either
import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.addFakeOverrides
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.overrides.FakeOverrideBuilder
import org.jetbrains.kotlin.backend.wasm.ir2wasm.getSuperClass
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.ir.IrStatement
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
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.impl.IrFakeOverrideFunctionImpl
import org.jetbrains.kotlin.ir.descriptors.WrappedSimpleFunctionDescriptor
import org.jetbrains.kotlin.ir.overrides.IrOverridingUtil
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.irConstructorCall
import org.jetbrains.kotlin.ir.util.isFakeOverriddenFromAny
import org.jetbrains.kotlin.ir.util.isSuspend
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.psi.KtPureClassOrObject
import org.jetbrains.kotlin.psi2ir.generators.ClassGenerator.Companion.sortedByRenderer

class AutoFactoryFakeOverrideTransformer(val anyClass: IrClassSymbol): IrElementTransformerVoid() {
    @OptIn(ObsoleteDescriptorBasedAPI::class)
    override fun visitFunction(declaration: IrFunction): IrStatement {
        return if (anyClass.getSimpleFunction(declaration.name.asString()) != null) {
            IrFakeOverrideFunctionImpl(
                startOffset = declaration.startOffset,
                endOffset = declaration.endOffset,
                origin = IrDeclarationOrigin.FAKE_OVERRIDE,
                name = declaration.name,
                visibility = declaration.visibility,
                modality = declaration.descriptor.modality,
                returnType = declaration.returnType,
                isInline = declaration.descriptor.isInline,
                isExternal = declaration.descriptor.isExternal,
                isTailrec = declaration.descriptor.isTailrec,
                isSuspend = declaration.descriptor.isSuspend,
                isOperator = declaration.descriptor.isOperator,
                isInfix = declaration.descriptor.isInfix,
                isExpect = declaration.descriptor.isExpect,
            ).apply {
                parent = declaration.parent

                acquireSymbol(IrSimpleFunctionSymbolImpl(WrappedSimpleFunctionDescriptor()))
            }

        } else {
            super.visitFunction(declaration)
        }
    }
}

open class AutoFactoryIrGenerator(
    private val compilerContext: IrPluginContext
): IrElementTransformerVoid(), ClassLoweringPass {
    @OptIn(ObsoleteDescriptorBasedAPI::class)
    override fun lower(irClass: IrClass) {
        if (irClass.name != AutoFactoryNames.factory) { return }

        val autoFactoryConstructor = irClass.parentAutoFactoryConstructor ?: return

        irClass.transformChildren(AutoFactoryFakeOverrideTransformer(anyClass = compilerContext.irBuiltIns.anyClass), null)

        val factoryConstructor = irClass.constructors.single { it.visibility == DescriptorVisibilities.PUBLIC }
        val factoryPrimaryConstructor = irClass.primaryConstructor!!
        val factoryCreateMethod = irClass.functions.single { it.name == AutoFactoryNames.createFun }
        // irClass.addFakeOverrides(compilerContext.irBuiltIns, listOf(factoryCreateMethod))

        // IrOverridingUtil(compilerContext.irBuiltIns, FakeOverrideBuilder())

        val newParametersAccess: List<Either<IrValueParameter, Pair<IrField, IrValueParameter>>> = autoFactoryConstructor.valueParameters.map { parameter ->
            if (parameter.hasAnnotation(AutoFactoryNames.Annotation.provided)) {
                val newParameter = factoryCreateMethod.valueParameters.single { it.name == parameter.name }
                Either.Left<IrValueParameter, Pair<IrField, IrValueParameter>>(newParameter)
            } else {
                val field = irClass.addField {
                    updateFrom(parameter)
                    name = parameter.name
                    type = parameter.type
                    visibility = DescriptorVisibilities.PRIVATE
                }
                val constructorParameter = factoryConstructor.valueParameters.single { it.name == parameter.name }
                Either.Right<IrValueParameter, Pair<IrField, IrValueParameter>>(field to constructorParameter)
            }
        }

        factoryConstructor.body = DeclarationIrBuilder(compilerContext, factoryConstructor.symbol).irBlockBody {
            +irDelegatingConstructorCall(
                if (factoryConstructor == factoryPrimaryConstructor) {
                    compilerContext.symbols.any.constructors.first().owner
                } else {
                    factoryPrimaryConstructor
                }
            )

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