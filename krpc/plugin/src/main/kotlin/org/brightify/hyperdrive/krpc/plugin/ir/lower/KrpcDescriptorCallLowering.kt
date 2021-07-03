package org.brightify.hyperdrive.krpc.plugin.ir.lower

import org.brightify.hyperdrive.krpc.plugin.KnownType
import org.brightify.hyperdrive.krpc.plugin.KrpcCall
import org.brightify.hyperdrive.krpc.plugin.KrpcIrElementTransformerVoidBase
import org.brightify.hyperdrive.krpc.plugin.ir.util.SerializerResolver
import org.brightify.hyperdrive.krpc.plugin.util.isKrpcDescriptorCall
import org.brightify.hyperdrive.krpc.plugin.ir.util.property
import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.declarations.addExtensionReceiver
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.util.irConstructorCall
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class KrpcDescriptorCallLowering(
    override val pluginContext: IrPluginContext,
    override val messageCollector: MessageCollector,
): KrpcIrElementTransformerVoidBase(), ClassLoweringPass {

    override fun lower(irClass: IrClass) {
        if (!irClass.isKrpcDescriptorCall) { return }

        val service = irClass.parentAsClass.parentAsClass
        val calls = getCalls(service)
        val descriptorClass = irClass.parentAsClass
        val serviceIdentifier = descriptorClass.property(KnownType.Nested.Descriptor.serviceIdentifier)
        val serializerResolver = SerializerResolver(pluginContext, service)

        for (property in irClass.properties) {
            val rpcCall = calls[property.name] ?: continue
            val requestWrapperType = KnownType.API.requestWrapper(rpcCall.requestType.count()).asClass().typeWith(rpcCall.requestType)
            val descriptorTypeParameters = listOfNotNull(
                requestWrapperType,
                rpcCall.upstreamFlowType?.element,
                rpcCall.downstreamFlowType?.element ?: rpcCall.returnType
            )
            property.getter!!.body = DeclarationIrBuilder(
                pluginContext,
                property.getter!!.symbol,
                startOffset = SYNTHETIC_OFFSET,
                endOffset = SYNTHETIC_OFFSET
            ).irBlockBody {
                +irReturn(
                    irConstructorCall(
                        irCall(
                            rpcCall.descriptorName.primaryConstructor,
                            rpcCall.descriptorName.asClass().typeWith(descriptorTypeParameters)
                        ).also { call ->
                            call.putValueArgument(0,
                                irCallConstructor(KnownType.API.serviceCallIdentifier.primaryConstructor, emptyList()).also { call ->
                                    call.putValueArgument(0, irCall(serviceIdentifier.getter!!).also {
                                        it.dispatchReceiver = irGetObject(descriptorClass.symbol)
                                    })
                                    call.putValueArgument(1, irString(property.name.asString()))
                                })

                            descriptorTypeParameters.forEachIndexed { index, type ->
                                call.putTypeArgument(index, type)
                            }

                            val arguments = listOfNotNull(
                                requestWrapperType,
                                rpcCall.upstreamFlowType?.element,
                                rpcCall.downstreamFlowType?.element ?: rpcCall.returnType
                            )

                            arguments.forEachIndexed { index, type ->
                                call.putValueArgument(
                                    index + 1,
                                    serializerResolver.resolveSerializer(this, type)
                                )
                            }
                            call.putValueArgument(arguments.count() + 1,
                                irConstructorCall(
                                    irCall(KnownType.API.rpcErrorSerializer.primaryConstructor).also { call ->
                                        appendExpectedErrorSerializers(call, rpcCall, property, serializerResolver)
                                    },
                                    KnownType.API.rpcErrorSerializer.primaryConstructor
                                )
                            )
                        },
                        rpcCall.descriptorName.primaryConstructor
                    )
                )
            }
        }
    }

    private fun appendExpectedErrorSerializers(
        call: IrConstructorCall,
        rpcCall: KrpcCall,
        property: IrProperty,
        serializerResolver: SerializerResolver,
    ) {
        if (rpcCall.expectedErrors.isEmpty()) { return }

        val polymorphicModuleBuilder = KnownType.Serialization.polymorphicModuleBuilder.asClass()
        val polymorphicModuleBuilderType =
            polymorphicModuleBuilder.typeWith(KnownType.API.rpcError.asClass().defaultType)
        val expectedErrorBuilder = pluginContext.irFactory.buildFun {
            this.name = Name.special("<anonymous>")
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
            returnType = pluginContext.irBuiltIns.unitType
            visibility = DescriptorVisibilities.LOCAL
            startOffset = SYNTHETIC_OFFSET
            endOffset = SYNTHETIC_OFFSET
        }.also {
            it.parent = property.getter!!
            val builder = it.addExtensionReceiver(polymorphicModuleBuilderType)
            it.body = DeclarationIrBuilder(pluginContext,
                it.symbol,
                startOffset = SYNTHETIC_OFFSET,
                endOffset = SYNTHETIC_OFFSET).irBlockBody {
                for (error in rpcCall.expectedErrors) {
                    +irCall(polymorphicModuleBuilder.getSimpleFunction("subclass")!!).also { call ->
                        call.dispatchReceiver = irGet(builder)
                        call.putTypeArgument(0, error)
                        call.putValueArgument(0,
                            IrClassReferenceImpl(startOffset,
                                endOffset,
                                context.irBuiltIns.kClassClass.starProjectedType,
                                error.classifier,
                                error))
                        call.putValueArgument(1, serializerResolver.resolveSerializer(this, error))
                    }
                }
            }
        }

        call.putValueArgument(0, IrFunctionExpressionImpl(
            call.startOffset,
            call.endOffset,
            pluginContext.referenceClass(FqName("kotlin.Function1"))!!
                .typeWith(polymorphicModuleBuilderType, pluginContext.irBuiltIns.unitType),
            expectedErrorBuilder,
            IrStatementOrigin.LAMBDA
        ))
    }
}