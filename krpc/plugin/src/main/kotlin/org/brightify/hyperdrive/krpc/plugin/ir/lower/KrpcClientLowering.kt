package org.brightify.hyperdrive.krpc.plugin.ir.lower

import org.brightify.hyperdrive.krpc.plugin.KnownType
import org.brightify.hyperdrive.krpc.plugin.KrpcIrElementTransformerVoidBase
import org.brightify.hyperdrive.krpc.plugin.util.isKrpcClient
import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.irConstructorCall
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationPluginContext

class KrpcClientLowering(
    override val pluginContext: IrPluginContext,
    override val messageCollector: MessageCollector,
): KrpcIrElementTransformerVoidBase(), ClassLoweringPass {

    override val compilerContext: SerializationPluginContext = pluginContext

    override fun lower(irClass: IrClass) {
        if (!irClass.isKrpcClient) { return }

        val constructor = irClass.constructors.single { it.visibility == DescriptorVisibilities.PUBLIC }
        val constructorTransportParameter = constructor.valueParameters.single()
        val transportField = irClass.addField {
            name = Name.identifier("transport")
            type = constructorTransportParameter.type
            visibility = DescriptorVisibilities.PRIVATE
            isFinal = true
            startOffset = SYNTHETIC_OFFSET
            endOffset = SYNTHETIC_OFFSET
        }
        val calls = getCalls(irClass.parentAsClass)
        val transport = pluginContext.referenceClass(KnownType.API.transport)!!
        val descriptorClass = irClass.parentAsClass.declarations.mapNotNull { it as? IrClass }.single { it.name == KnownType.Nested.descriptor }
        val descriptorCallClass = descriptorClass.declarations.mapNotNull { it as? IrClass }.single { it.name == KnownType.Nested.call }

        constructor.body = DeclarationIrBuilder(pluginContext,
            constructor.symbol,
            startOffset = SYNTHETIC_OFFSET,
            endOffset = SYNTHETIC_OFFSET).irBlockBody {
            +irDelegatingConstructorCall(pluginContext.symbols.any.constructors.first().owner)

            +irSetField(irGet(irClass.thisReceiver!!), transportField, irGet(constructorTransportParameter))
        }

        for (function in irClass.functions) {
            val callDescription = descriptorCallClass.properties.singleOrNull { it.name == function.name } ?: continue
            val rpcCall = calls[function.name] ?: continue
            function.body = DeclarationIrBuilder(pluginContext,
                function.symbol,
                startOffset = SYNTHETIC_OFFSET,
                endOffset = SYNTHETIC_OFFSET).irBlockBody {
                val getDispatchReceiver = irGet(function.dispatchReceiverParameter!!)

                val requestWrapper = KnownType.API.requestWrapper(rpcCall.requestType.count()).asClass()
                +irReturn(
                    irCall(
                        transport.functions.single { it.owner.name == rpcCall.transportFunctionName }.owner.symbol,
                        rpcCall.downstreamFlowType?.element ?: rpcCall.returnType,
                        listOfNotNull(
                            requestWrapper.typeWith(rpcCall.requestType),
                            rpcCall.upstreamFlowType?.element,
                            rpcCall.downstreamFlowType?.element ?: rpcCall.returnType
                        )
                    ).also { call ->
                        call.dispatchReceiver = irGetField(getDispatchReceiver, transportField)

                        call.putValueArgument(0, irCall(callDescription.getter!!).also {
                            it.dispatchReceiver = irGetObject(descriptorCallClass.symbol)
                        })
                        call.putValueArgument(1, if (rpcCall.requestType.isEmpty()) {
                            irGetObject(pluginContext.irBuiltIns.unitClass)
                        } else {
                            val requestWrapperConstructor = requestWrapper.constructors.single { it.owner.isPrimary }
                            irConstructorCall(
                                irCall(
                                    requestWrapperConstructor,
                                    requestWrapper.typeWith(rpcCall.requestType)
                                ).also { requestWrapperConstructor ->
                                    for (index in rpcCall.requestType.indices) {
                                        requestWrapperConstructor.putTypeArgument(index, function.valueParameters[index].type)
                                        requestWrapperConstructor.putValueArgument(index, irGet(function.valueParameters[index]))
                                    }
                                },
                                requestWrapperConstructor
                            )
                        })
                        if (rpcCall.upstreamFlowType != null) {
                            // TODO: Unsafe
                            call.putValueArgument(2, irGet(function.valueParameters.last()))
                        }
                    }
                )
            }
        }
    }
}