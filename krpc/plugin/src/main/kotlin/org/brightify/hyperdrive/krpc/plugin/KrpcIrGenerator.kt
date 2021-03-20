package org.brightify.hyperdrive.krpc.plugin

import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.declarations.addExtensionReceiver
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irUnit
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.interpreter.toIrConst
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.util.irConstructorCall
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class KrpcIrGenerator(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector
): IrElementTransformerVoid(), ClassLoweringPass {

    private val flowType by lazy { pluginContext.referenceClass(KnownType.Coroutines.flow)!! }

    override fun lower(irClass: IrClass) {
        messageCollector.report(CompilerMessageSeverity.ERROR, irClass.dump())
        when {
            irClass.isKrpcClient -> {
                val constructor = irClass.constructors.single { it.visibility == DescriptorVisibilities.PUBLIC }
                val constructorTransportParameter = constructor.valueParameters.single()
                val transportField = irClass.addField {
                    name = Name.identifier("transport")
                    type = constructorTransportParameter.type
                    visibility = DescriptorVisibilities.PRIVATE
                    isFinal = true
                }
                val calls = getCalls(irClass.parentAsClass)
                val transport = pluginContext.referenceClass(KnownType.API.transport)!!
                val descriptorClass = irClass.parentAsClass.declarations.mapNotNull { it as? IrClass }.single { it.name == KnownType.Nested.descriptor }
                val descriptorCallClass = descriptorClass.declarations.mapNotNull { it as? IrClass }.single { it.name == KnownType.Nested.call }

                constructor.body = DeclarationIrBuilder(pluginContext, constructor.symbol).irBlockBody {
                    +irDelegatingConstructorCall(pluginContext.symbols.any.constructors.first().owner)

                    +irSetField(irGet(irClass.thisReceiver!!), transportField, irGet(constructorTransportParameter))
                }

                for (function in irClass.functions) {
                    val callDescription = descriptorCallClass.properties.singleOrNull { it.name == function.name } ?: continue
                    val rpcCall = calls[function.name] ?: continue
                    function.body = DeclarationIrBuilder(pluginContext, function.symbol).irBlockBody {
                        val getDispatchReceiver = irGet(function.dispatchReceiverParameter!!)

                        val requestWrapper = pluginContext.referenceClass(KnownType.API.requestWrapper(rpcCall.requestType.count()))!!
                        +irReturn(
                            irCall(
                                transport.functions.single { it.owner.name == rpcCall.transportFunctionName },
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
                                            requestWrapperConstructor.owner
                                        ).also { call ->
                                            for (index in rpcCall.requestType.indices) {
                                                call.putTypeArgument(index, function.valueParameters[index].type)
                                                call.putValueArgument(index, irGet(function.valueParameters[index]))
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
            irClass.isKrpcDescriptor -> {
                val calls = getCalls(irClass.parentAsClass)

                val serviceClass = irClass.parentAsClass
                val descriptorCallClass = irClass.declarations.mapNotNull { it as? IrClass }.single { it.name == KnownType.Nested.call }

                val serviceIdentifier = irClass.property(KnownType.Nested.Descriptor.serviceIdentifier)
                serviceIdentifier.getter!!.body = DeclarationIrBuilder(pluginContext, serviceIdentifier.getter!!.symbol).irBlockBody {
                    +irReturn(
                        irString(serviceClass.name.asString())
                    )
                }

                val describe = irClass.functions.single { it.name == KnownType.Nested.Descriptor.describe && !it.isFakeOverride }
                val serviceParameter = describe.valueParameters.single()
                describe.body = DeclarationIrBuilder(pluginContext, describe.symbol).irBlockBody {
                    +irReturn(
                        irCallConstructor(
                            KnownType.API.serviceDescription.asSingleFqName().primaryConstructor,
                            emptyList()
                        ).also { call ->
                            call.putValueArgument(0, irCall(serviceIdentifier.getter!!).also { it.dispatchReceiver = irGet(describe.dispatchReceiverParameter!!) })
                            call.putValueArgument(1, irCall(
                                KnownType.Kotlin.listOf.asFunction { it.owner.typeParameters.count() == 1 && it.owner.valueParameters.singleOrNull()?.isVararg ?: false },
                                KnownType.API.callDescriptor.asClass().starProjectedType
                            ).also { listCall ->
                                listCall.putTypeArgument(0, KnownType.API.callDescriptor.asClass().starProjectedType)
                                listCall.putValueArgument(0, IrVarargImpl(
                                    listCall.startOffset,
                                    listCall.endOffset,
                                    pluginContext.irBuiltIns.arrayClass.typeWith(KnownType.API.callDescriptor.asClass().defaultType),
                                    KnownType.API.callDescriptor.asClass().defaultType,
                                    calls.map { (name, rpcCall) ->

                                        irCall(rpcCall.descriptorName.asClass().getSimpleFunction("calling")!!).also { call ->
                                            val requestWrapperClass = KnownType.API.requestWrapper(rpcCall.requestType.count()).asClass()
                                            val requestWrapperType = requestWrapperClass.typeWith(rpcCall.requestType)
                                            val caller = pluginContext.irFactory.buildFun {
                                                isSuspend = true
                                                this.name = Name.special("<anonymous>")
                                                origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
                                                returnType = rpcCall.returnType
                                                visibility = DescriptorVisibilities.LOCAL
                                            }.also {
                                                it.parent = describe
                                                val request = it.addValueParameter("request", requestWrapperType)
                                                val clientFlow = rpcCall.upstreamFlowType?.let { flow ->
                                                    it.addValueParameter("clientStream", flow.flow)
                                                }
                                                it.body = DeclarationIrBuilder(pluginContext, it.symbol).irBlockBody {
                                                    +irReturn(
                                                        irCall(
                                                            serviceClass.functions.single { it.name == name }
                                                        ).also { call ->
                                                            call.dispatchReceiver = irGet(serviceParameter)

                                                            for (index in rpcCall.requestType.indices) {
                                                                call.putValueArgument(index, irCall(requestWrapperClass.functions.single { it.owner.name == Name.identifier("component${index + 1}") }).also { call ->
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

                                            call.dispatchReceiver = irCall(descriptorCallClass.property(name).getter!!).also { it.dispatchReceiver = irGetObject(descriptorCallClass.symbol) }
                                            call.putValueArgument(0, IrFunctionExpressionImpl(
                                                call.startOffset,
                                                call.endOffset,
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
                        }
                    )
                }
            }
            irClass.isKrpcDescriptorCall -> {
                val calls = getCalls(irClass.parentAsClass.parentAsClass)
                val descriptorClass = irClass.parentAsClass
                val serviceIdentifier = descriptorClass.property(KnownType.Nested.Descriptor.serviceIdentifier)

                for (property in irClass.properties) {
                    val rpcCall = calls[property.name] ?: continue
                    val serializer = KnownType.Serialization.serializer.asFunction {
                        it.owner.valueParameters.isEmpty() &&
                            it.owner.typeParameters.count() == 1 &&
                            it.owner.dispatchReceiverParameter == null &&
                            it.owner.extensionReceiverParameter == null
                    }
                    val requestWrapperType = KnownType.API.requestWrapper(rpcCall.requestType.count()).asClass().typeWith(rpcCall.requestType)
                    property.getter!!.body = DeclarationIrBuilder(pluginContext, property.getter!!.symbol).irBlockBody {
                        +irReturn(
                            irConstructorCall(
                                irCall(
                                    rpcCall.descriptorName.primaryConstructor
                                ).also { call ->
                                    call.putValueArgument(0, irCallConstructor(KnownType.API.serviceCallIdentifier.primaryConstructor, emptyList()).also { call ->
                                        call.putValueArgument(0, irCall(serviceIdentifier.getter!!).also {
                                            it.dispatchReceiver = irGetObject(descriptorClass.symbol)
                                        })
                                        call.putValueArgument(1, irString(property.name.asString()))
                                    })

                                    listOfNotNull(
                                        requestWrapperType,
                                        rpcCall.upstreamFlowType?.element,
                                        rpcCall.downstreamFlowType?.element ?: rpcCall.returnType
                                    ).forEachIndexed { index, type ->
                                        call.putTypeArgument(index, type)
                                    }

                                    val arguments = listOfNotNull(
                                        requestWrapperType,
                                        rpcCall.upstreamFlowType?.element,
                                        rpcCall.downstreamFlowType?.element ?: rpcCall.returnType
                                    )

                                    arguments.forEachIndexed { index, type ->
                                        call.putValueArgument(index + 1,
                                            irCall(serializer).also { call ->
                                                call.putTypeArgument(0, type)
                                            }
                                        )
                                    }
                                    call.putValueArgument(arguments.count() + 1,
                                        irCallConstructor(KnownType.API.rpcErrorSerializer.primaryConstructor, emptyList()).also { call ->
                                            if (rpcCall.expectedErrors.isNotEmpty()) {
                                                val polymorphicModuleBuilder = KnownType.Serialization.polymorphicModuleBuilder.asClass()
                                                val polymorphicModuleBuilderType = polymorphicModuleBuilder.typeWith(KnownType.API.rpcError.asClass().defaultType)
                                                val expectedErrorBuilder = pluginContext.irFactory.buildFun {
                                                    this.name = Name.special("<anonymous>")
                                                    origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
                                                    returnType = pluginContext.irBuiltIns.unitType
                                                    visibility = DescriptorVisibilities.LOCAL
                                                }.also {
                                                    it.parent = property.getter!!
                                                    val builder = it.addExtensionReceiver(polymorphicModuleBuilderType)
                                                    it.body = DeclarationIrBuilder(pluginContext, it.symbol).irBlockBody {
                                                        for (error in rpcCall.expectedErrors) {
                                                            +irCall(polymorphicModuleBuilder.getSimpleFunction("subclass")!!).also { call ->
                                                                call.dispatchReceiver = irGet(builder)
                                                                call.putTypeArgument(0, error)
                                                                call.putValueArgument(0, IrClassReferenceImpl(call.startOffset, call.endOffset, error, error.classifier, error))
                                                                call.putValueArgument(1, irCall(serializer).also { call ->
                                                                    call.putTypeArgument(0, error)
                                                                })
                                                            }
                                                        }
                                                    }
                                                }

                                                call.putValueArgument(0, IrFunctionExpressionImpl(
                                                    call.startOffset,
                                                    call.endOffset,
                                                    pluginContext.irBuiltIns.function(1).typeWith(polymorphicModuleBuilderType, pluginContext.irBuiltIns.unitType),
                                                    expectedErrorBuilder,
                                                    IrStatementOrigin.LAMBDA
                                                ))
                                            }
                                        }
                                    )
                                },
                                rpcCall.descriptorName.primaryConstructor
                            )
                        )
                    }
                }

            }
        }
        println("================== BEGIN <${irClass.name.asString()}> ==================")
        try {
            println(irClass.dumpKotlinLike())
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        println("==================")
        try {
            println(irClass.dump())
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        println("================== END <${irClass.name.asString()}> ==================")
    }

    private val FqName.primaryConstructor: IrConstructorSymbol
        get() = pluginContext.referenceConstructors(this).single { it.owner.isPrimary }

    private fun FqName.asClass(): IrClassSymbol = pluginContext.referenceClass(this)!!

    private fun FqName.asFunction(filter: (IrSimpleFunctionSymbol) -> Boolean) = pluginContext.referenceFunctions(this).single(filter)

    private fun FqName.asFunctions(): Collection<IrFunctionSymbol> = pluginContext.referenceFunctions(this)

    private fun IrClass.property(name: Name): IrProperty =
        properties.single { it.name == name }

    private fun getCalls(irClass: IrClass): Map<Name, KrpcCall_> {
        return irClass.functions.mapNotNull { function ->
            if (!function.isSuspend) {
                messageCollector.report(CompilerMessageSeverity.ERROR, "Only suspending methods are supported!")
                return@mapNotNull null
            }

            val lastParam = function.valueParameters.lastOrNull()
            val lastParamType = lastParam?.type
            val (clientRequestParameters, clientStreamingFlow) = if (lastParamType != null && isFlow(lastParamType)) {
                function.valueParameters.dropLast(1) to lastParamType
            } else {
                function.valueParameters to null
            }
            val expectedErrors = getExpectedErrors(function)
            val returnType = function.returnType

            function.name to when {
                clientStreamingFlow != null && isFlow(returnType) -> {
                    KrpcCall_(
                        function,
                        Name.identifier("biStream"),
                        KnownType.API.coldBistreamCallDescriptor,
                        expectedErrors,
                        clientRequestParameters.map { it.type },
                        KrpcCall_.FlowType(clientStreamingFlow),
                        KrpcCall_.FlowType(returnType),
                        returnType,
                    )

                    // KrpcCall.BiStream(function, expectedErrors, clientRequestParameters, clientStreamingFlow, returnType.arguments.single().typeOrNull!!)
                }
                clientStreamingFlow != null -> {
                    KrpcCall_(
                        function,
                        Name.identifier("clientStream"),
                        KnownType.API.coldUpstreamCallDescriptor,
                        expectedErrors,
                        clientRequestParameters.map { it.type },
                        KrpcCall_.FlowType(clientStreamingFlow),
                        null,
                        returnType
                    )

                    // KrpcCall.ClientStream(function, expectedErrors, clientRequestParameters, clientStreamingFlow, function.returnType)
                }
                isFlow(returnType) -> {
                    KrpcCall_(
                        function,
                        Name.identifier("serverStream"),
                        KnownType.API.coldDownstreamCallDescriptor,
                        expectedErrors,
                        clientRequestParameters.map { it.type },
                        null,
                        KrpcCall_.FlowType(returnType),
                        returnType
                    )

                    // KrpcCall.ServerStream(function, expectedErrors, clientRequestParameters, returnType.arguments.single().typeOrNull!!)
                }
                else -> {
                    KrpcCall_(
                        function,
                        Name.identifier("singleCall"),
                        KnownType.API.clientCallDescriptor,
                        expectedErrors,
                        clientRequestParameters.map { it.type },
                        null,
                        null,
                        returnType
                    )

                    // KrpcCall.SingleCall(function, expectedErrors, clientRequestParameters, function.returnType)
                }
            }
        }.toMap()
    }

    private fun getExpectedErrors(function: IrSimpleFunction): List<IrSimpleType> {
        class ErrorAnnotationVisitor: IrElementTransformerVoid() {
            var expectedErrors = emptyList<IrSimpleType>()
                private set

            override fun visitVararg(expression: IrVararg): IrExpression {
                expectedErrors += expression.elements
                    .mapNotNull {
                        val elementExpression = it as? IrExpression ?: return@mapNotNull null
                        val elementSimpleType = elementExpression.type as? IrSimpleType ?: return@mapNotNull null
                        elementSimpleType.arguments.singleOrNull() as? IrSimpleType
                    }

                return super.visitVararg(expression)
            }
        }
        val visitor = ErrorAnnotationVisitor()
        val annotations = listOfNotNull(
            function.getAnnotation(KnownType.Annotation.error),
            function.returnType.getAnnotation(KnownType.Annotation.error),
        ).toMutableList()
        annotations += function.valueParameters.mapNotNull { it.getAnnotation(KnownType.Annotation.error) }
        annotations += function.valueParameters.mapNotNull { it.type.getAnnotation(KnownType.Annotation.error) }
        annotations.forEach { it.getValueArgument(0)?.accept(visitor, null) }
        return visitor.expectedErrors
    }

    @OptIn(ExperimentalContracts::class)
    private fun isFlow(type: IrType): Boolean {
        contract {
            returns(true) implies (type is IrSimpleType)
        }
        return type.isSubtypeOfClass(flowType)
    }

}