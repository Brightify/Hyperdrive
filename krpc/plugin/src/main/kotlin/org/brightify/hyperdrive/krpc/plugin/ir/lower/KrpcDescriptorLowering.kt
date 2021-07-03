package org.brightify.hyperdrive.krpc.plugin.ir.lower

import org.brightify.hyperdrive.krpc.plugin.KnownType
import org.brightify.hyperdrive.krpc.plugin.KrpcIrElementTransformerVoidBase
import org.brightify.hyperdrive.krpc.plugin.util.isKrpcDescriptor
import org.brightify.hyperdrive.krpc.plugin.ir.util.property
import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.util.irConstructorCall
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class KrpcDescriptorLowering(
    override val pluginContext: IrPluginContext,
    override val messageCollector: MessageCollector,
): KrpcIrElementTransformerVoidBase(), ClassLoweringPass {

    override fun lower(irClass: IrClass) {
        if (!irClass.isKrpcDescriptor) { return }

        val calls = getCalls(irClass.parentAsClass)

        val serviceClass = irClass.parentAsClass
        val descriptorCallClass = irClass.declarations.mapNotNull { it as? IrClass }.single { it.name == KnownType.Nested.call }

        val serviceIdentifier = irClass.property(KnownType.Nested.Descriptor.serviceIdentifier)
        serviceIdentifier.getter!!.body = DeclarationIrBuilder(pluginContext,
            serviceIdentifier.getter!!.symbol,
            startOffset = SYNTHETIC_OFFSET,
            endOffset = SYNTHETIC_OFFSET).irBlockBody {
            +irReturn(
                irString(serviceClass.name.asString())
            )
        }

        val describe = irClass.functions.single { it.name == KnownType.Nested.Descriptor.describe && !it.isFakeOverride }
        val serviceParameter = describe.valueParameters.single()
        describe.body = DeclarationIrBuilder(pluginContext, describe.symbol, startOffset = SYNTHETIC_OFFSET, endOffset = SYNTHETIC_OFFSET).irBlockBody {
            +irReturn(
                irConstructorCall(
                    irCall(
                        KnownType.API.serviceDescription.asSingleFqName().primaryConstructor,
                        KnownType.API.serviceDescription.asSingleFqName().asClass().defaultType
                    ).also { call ->
                        call.putValueArgument(0,
                            irCall(serviceIdentifier.getter!!).also { it.dispatchReceiver = irGet(describe.dispatchReceiverParameter!!) })
                        call.putValueArgument(1, irCall(
                            KnownType.Kotlin.listOf.asFunction { it.owner.typeParameters.count() == 1 && it.owner.valueParameters.singleOrNull()?.isVararg ?: false },
                            KnownType.Kotlin.list.asClass().typeWith(KnownType.API.runnableCallDescription.asClass().starProjectedType)
                        ).also { listCall ->
                            listCall.putTypeArgument(0, KnownType.API.runnableCallDescription.asClass().starProjectedType)
                            listCall.putValueArgument(0, IrVarargImpl(
                                listCall.startOffset,
                                listCall.endOffset,
                                pluginContext.irBuiltIns.arrayClass.typeWith(KnownType.API.runnableCallDescription.asClass().starProjectedType),
                                KnownType.API.runnableCallDescription.asClass().starProjectedType,
                                calls.map { (name, rpcCall) ->

                                    irCall(
                                        rpcCall.descriptorName.asClass().getSimpleFunction("calling")!!,
                                        KnownType.API.runnableCallDescription.asClass().starProjectedType
                                    ).also { call ->
                                        val requestWrapperClass = KnownType.API.requestWrapper(rpcCall.requestType.count()).asClass()
                                        val requestWrapperType = requestWrapperClass.typeWith(rpcCall.requestType)
                                        val caller = pluginContext.irFactory.buildFun {
                                            isSuspend = true
                                            this.name = Name.special("<anonymous>")
                                            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
                                            returnType = rpcCall.returnType
                                            visibility = DescriptorVisibilities.LOCAL
                                            startOffset = SYNTHETIC_OFFSET
                                            endOffset = SYNTHETIC_OFFSET
                                        }.also {
                                            it.parent = describe
                                            val request = it.addValueParameter("request", requestWrapperType)
                                            val clientFlow = rpcCall.upstreamFlowType?.let { flow ->
                                                it.addValueParameter("clientStream", flow.flow)
                                            }
                                            it.body = DeclarationIrBuilder(pluginContext,
                                                it.symbol,
                                                startOffset = SYNTHETIC_OFFSET,
                                                endOffset = SYNTHETIC_OFFSET).irBlockBody {
                                                +irReturn(
                                                    irCall(
                                                        serviceClass.functions.single { it.name == name }
                                                    ).also { call ->
                                                        call.dispatchReceiver = irGet(serviceParameter)

                                                        for ((index, type) in rpcCall.requestType.withIndex()) {
                                                            call.putValueArgument(index, irCall(
                                                                requestWrapperClass.functions.single { it.owner.name == Name.identifier("component${index + 1}") },
                                                                type
                                                            ).also { call ->
                                                                call.dispatchReceiver = irGet(request)
                                                            })
                                                        }

                                                        if (clientFlow != null) {
                                                            call.putValueArgument(call.valueArgumentsCount - 1, irGet(clientFlow))
                                                        }
                                                    }
                                                )
                                            }
                                        }

                                        call.dispatchReceiver = irCall(descriptorCallClass.property(name).getter!!).also {
                                            it.dispatchReceiver = irGetObject(descriptorCallClass.symbol)
                                        }
                                        call.putValueArgument(0, IrFunctionExpressionImpl(
                                            irClass.startOffset,
                                            irClass.endOffset,
                                            pluginContext.referenceClass(FqName("kotlin.coroutines.SuspendFunction1"))!!.typeWith(
                                                listOfNotNull(
                                                    requestWrapperType,
                                                    rpcCall.upstreamFlowType?.element,
                                                    rpcCall.returnType
                                                )
                                            ),
                                            caller,
                                            IrStatementOrigin.LAMBDA
                                        ))
                                    }
                                }
                            ))
                        })
                    },
                    KnownType.API.serviceDescription.asSingleFqName().primaryConstructor
                )
            )
        }
    }
}