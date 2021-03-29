package org.brightify.hyperdrive.krpc.plugin

import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.jvm.codegen.fileParent
import org.jetbrains.kotlin.backend.jvm.ir.propertyIfAccessor
import org.jetbrains.kotlin.backend.wasm.ir2wasm.bind
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
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
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.getClassTypeArguments
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.toKotlinType
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.companionObject
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
import org.jetbrains.kotlin.js.translate.utils.AnnotationsUtils
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.descriptorUtil.firstArgument
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.representativeUpperBound
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlinx.serialization.compiler.backend.common.AbstractSerialGenerator
import org.jetbrains.kotlinx.serialization.compiler.backend.common.findStandardKotlinTypeSerializer
import org.jetbrains.kotlinx.serialization.compiler.backend.ir.IrBuilderExtension
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationPluginContext
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class KrpcIrGenerator(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector,
    private val printIR: Boolean,
    private val printKotlinLike: Boolean,
): IrElementTransformerVoid(), ClassLoweringPass, IrBuilderExtension {

    class SerialHelper(bindingContext: BindingContext, currentDeclaration: ClassDescriptor): AbstractSerialGenerator(bindingContext, currentDeclaration)

    override val compilerContext: SerializationPluginContext = pluginContext

    private val flowType by lazy { pluginContext.referenceClass(KnownType.Coroutines.flow)!! }

    @OptIn(ObsoleteDescriptorBasedAPI::class)
    override fun lower(irClass: IrClass) {
        when {
            irClass.isKrpcClient -> {
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

                constructor.body = DeclarationIrBuilder(pluginContext, constructor.symbol, startOffset = SYNTHETIC_OFFSET, endOffset = SYNTHETIC_OFFSET).irBlockBody {
                    +irDelegatingConstructorCall(pluginContext.symbols.any.constructors.first().owner)

                    +irSetField(irGet(irClass.thisReceiver!!), transportField, irGet(constructorTransportParameter))
                }

                for (function in irClass.functions) {
                    val callDescription = descriptorCallClass.properties.singleOrNull { it.name == function.name } ?: continue
                    val rpcCall = calls[function.name] ?: continue
                    function.body = DeclarationIrBuilder(pluginContext, function.symbol, startOffset = SYNTHETIC_OFFSET, endOffset = SYNTHETIC_OFFSET).irBlockBody {
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
                serviceIdentifier.getter!!.body = DeclarationIrBuilder(pluginContext, serviceIdentifier.getter!!.symbol, startOffset = SYNTHETIC_OFFSET, endOffset = SYNTHETIC_OFFSET).irBlockBody {
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
                                call.putValueArgument(0, irCall(serviceIdentifier.getter!!).also { it.dispatchReceiver = irGet(describe.dispatchReceiverParameter!!) })
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
                                                    it.body = DeclarationIrBuilder(pluginContext, it.symbol, startOffset = SYNTHETIC_OFFSET, endOffset = SYNTHETIC_OFFSET).irBlockBody {
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

                                                call.dispatchReceiver = irCall(descriptorCallClass.property(name).getter!!).also { it.dispatchReceiver = irGetObject(descriptorCallClass.symbol) }
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
            irClass.isKrpcDescriptorCall -> {
                val service = irClass.parentAsClass.parentAsClass
                val calls = getCalls(service)
                val descriptorClass = irClass.parentAsClass
                val serviceIdentifier = descriptorClass.property(KnownType.Nested.Descriptor.serviceIdentifier)
                val serialHelper = SerialHelper(pluginContext.bindingContext, irClass.descriptor)

                fun toClassDescriptor(type: KotlinType?): ClassDescriptor? {
                    return type?.constructor?.declarationDescriptor?.let { descriptor ->
                        when(descriptor) {
                            is ClassDescriptor -> descriptor
                            is TypeParameterDescriptor -> toClassDescriptor(descriptor.representativeUpperBound)
                            else -> null
                        }
                    }
                }

                val additionalSerializers: Map<Pair<ClassDescriptor, Boolean>, ClassDescriptor> by lazy {
                    fun getKClassListFromFileAnnotation(annotationFqName: FqName, declarationInFile: DeclarationDescriptor): List<KotlinType> {
                        val annotation = AnnotationsUtils.getContainingFileAnnotations(pluginContext.bindingContext, declarationInFile)
                            .find { it.fqName == annotationFqName } ?: return emptyList()

                        val typeList: List<KClassValue> = annotation.firstArgument()?.value as? List<KClassValue> ?: return emptyList()
                        return typeList.map { it.getArgumentType(declarationInFile.module) }
                    }

                    fun isKSerializer(type: KotlinType?): Boolean =
                        type != null && KotlinBuiltIns.isConstructedFromGivenClass(type, KnownType.Serialization.kserializer)

                    getKClassListFromFileAnnotation(KnownType.Serialization.useSerializers, service.descriptor)
                        .associateBy(
                            {
                                val kotlinType = it.supertypes().find(::isKSerializer)?.arguments?.firstOrNull()?.type
                                val descriptor = toClassDescriptor(kotlinType) ?: throw AssertionError("Argument for ${KnownType.Serialization.useSerializers} does not implement KSerializer or does not provide serializer for concrete type")
                                descriptor to kotlinType!!.isMarkedNullable
                            },
                            { toClassDescriptor(it)!! }
                        )
                }

                for (property in irClass.properties) {
                    val rpcCall = calls[property.name] ?: continue
                    val serializer = KnownType.Serialization.serializer.asFunction {
                        it.owner.valueParameters.isEmpty() &&
                            it.owner.typeParameters.count() == 1 &&
                            it.owner.dispatchReceiverParameter == null &&
                            it.owner.extensionReceiverParameter == null
                    }
                    val requestWrapperType = KnownType.API.requestWrapper(rpcCall.requestType.count()).asClass().typeWith(rpcCall.requestType)
                    val descriptorTypeParameters = listOfNotNull(
                        requestWrapperType,
                        rpcCall.upstreamFlowType?.element,
                        rpcCall.downstreamFlowType?.element ?: rpcCall.returnType
                    )
                    property.getter!!.body = DeclarationIrBuilder(pluginContext, property.getter!!.symbol, startOffset = SYNTHETIC_OFFSET, endOffset = SYNTHETIC_OFFSET).irBlockBody {
                        +irReturn(
                            irConstructorCall(
                                irCall(
                                    rpcCall.descriptorName.primaryConstructor,
                                    rpcCall.descriptorName.asClass().typeWith(descriptorTypeParameters)
                                ).also { call ->
                                    call.putValueArgument(0, irCallConstructor(KnownType.API.serviceCallIdentifier.primaryConstructor, emptyList()).also { call ->
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

                                    val builtinSerializers = pluginContext.referenceFunctions(KnownType.Serialization.builtinSerializer).associateBy { it.owner.extensionReceiverParameter?.type }
                                    fun serializerExpressionFor(type: IrType): IrExpression {
                                        val kotlinType = type.toKotlinType()
                                        val key = toClassDescriptor(kotlinType) to kotlinType.isMarkedNullable

                                        val additionalSerializer: IrExpression? by lazy {
                                            additionalSerializers[key]?.let {
                                                it.fqNameOrNull()?.let(pluginContext::referenceClass)
                                            }?.let { additionalSerializer ->
                                                irConstructorCall(
                                                    irCall(
                                                        additionalSerializer.primaryConstructor
                                                    ),
                                                    additionalSerializer.primaryConstructor
                                                )
                                            }
                                        }

                                        val companionSerializer: IrExpression? by lazy {
                                            type.getClass()?.let {
                                                it.companionObject()?.functions?.singleOrNull { it.name.asString() == "serializer" }
                                            }?.let { companionSerializer ->
                                                irCall(
                                                    companionSerializer.symbol,
                                                    type,
                                                    (type as IrSimpleType).arguments.map { it.typeOrNull!! }
                                                ).also { call ->
                                                    call.dispatchReceiver = irGetObject(type.getClass()?.companionObject()!!.symbol)
                                                    for ((index, parameter) in (type as IrSimpleType).arguments.withIndex()) {
                                                        call.putValueArgument(index, serializerExpressionFor(parameter.typeOrNull!!))
                                                    }
                                                }
                                            }
                                        }

                                        val builtinSerializer: IrExpression? by lazy {
                                            findStandardKotlinTypeSerializer(pluginContext.moduleDescriptor, kotlinType)?.let {
                                                it.fqNameOrNull()?.let(pluginContext::referenceClass)
                                            }?.let { builtinSerializer ->
                                                irGetObject(builtinSerializer)
                                            }
                                        }

                                        val fallbackSerializer: IrExpression by lazy {
                                            val kserializer = KnownType.Serialization.kserializer.asClass().typeWith(type)
                                            irCall(
                                                serializer,
                                                kserializer
                                            ).also { call ->
                                                call.putTypeArgument(0, type)
                                            }
                                        }

                                        return additionalSerializer ?:
                                            companionSerializer ?:
                                            builtinSerializer ?:
                                            fallbackSerializer
                                    }

                                    arguments.forEachIndexed { index, type ->
                                        call.putValueArgument(
                                            index + 1,
                                            serializerExpressionFor(type)
                                        )
                                    }
                                    call.putValueArgument(arguments.count() + 1,
                                        irConstructorCall(
                                            irCall(
                                                KnownType.API.rpcErrorSerializer.primaryConstructor
                                            ).also { call ->
                                                if (rpcCall.expectedErrors.isNotEmpty()) {
                                                    val polymorphicModuleBuilder = KnownType.Serialization.polymorphicModuleBuilder.asClass()
                                                    val polymorphicModuleBuilderType = polymorphicModuleBuilder.typeWith(KnownType.API.rpcError.asClass().defaultType)
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
                                                        it.body = DeclarationIrBuilder(pluginContext, it.symbol, startOffset = SYNTHETIC_OFFSET, endOffset = SYNTHETIC_OFFSET).irBlockBody {
                                                            for (error in rpcCall.expectedErrors) {
                                                                +irCall(polymorphicModuleBuilder.getSimpleFunction("subclass")!!).also { call ->
                                                                    call.dispatchReceiver = irGet(builder)
                                                                    call.putTypeArgument(0, error)
                                                                    call.putValueArgument(0, IrClassReferenceImpl(startOffset, endOffset, context.irBuiltIns.kClassClass.starProjectedType, error.classifier, error))
                                                                    val kserializer = KnownType.Serialization.kserializer.asClass().typeWith(error)
                                                                    call.putValueArgument(1, irCall(serializer, kserializer).also { call ->
                                                                        call.putTypeArgument(0, error)

                                                                    })
                                                                }
                                                            }
                                                        }
                                                    }

                                                    call.putValueArgument(0, IrFunctionExpressionImpl(
                                                        call.startOffset,
                                                        call.endOffset,
                                                        pluginContext.referenceClass(FqName("kotlin.Function1"))!!.typeWith(polymorphicModuleBuilderType, pluginContext.irBuiltIns.unitType),
                                                        expectedErrorBuilder,
                                                        IrStatementOrigin.LAMBDA
                                                    ))
                                                }
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
        }

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

    private val FqName.primaryConstructor: IrConstructorSymbol
        get() = pluginContext.referenceConstructors(this).single { it.owner.isPrimary }

    private val IrClassSymbol.primaryConstructor: IrConstructorSymbol
        get() = this.constructors.first { it.owner.isPrimary }

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
                        KnownType.API.coldBistreamCallDescription,
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
                        KnownType.API.coldUpstreamCallDescription,
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
                        KnownType.API.coldDownstreamCallDescription,
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
                        KnownType.API.singleCallDescription,
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