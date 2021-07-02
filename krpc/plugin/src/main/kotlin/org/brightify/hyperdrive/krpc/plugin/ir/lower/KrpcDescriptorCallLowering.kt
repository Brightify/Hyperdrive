package org.brightify.hyperdrive.krpc.plugin

import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
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
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.toKotlinType
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.util.irConstructorCall
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.js.translate.utils.AnnotationsUtils
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.descriptorUtil.firstArgument
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.representativeUpperBound
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlinx.serialization.compiler.backend.common.findStandardKotlinTypeSerializer

@OptIn(ObsoleteDescriptorBasedAPI::class)
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
            property.getter!!.body = DeclarationIrBuilder(pluginContext,
                property.getter!!.symbol,
                startOffset = SYNTHETIC_OFFSET,
                endOffset = SYNTHETIC_OFFSET).irBlockBody {
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

                            fun serializerExpressionFor(type: IrType): IrExpression {
                                val kotlinType = type.toKotlinType()
                                val key = toClassDescriptor(kotlinType) to kotlinType.isMarkedNullable

                                fun irSerializerConstructorCall(symbol: IrFunctionSymbol): IrExpression {
                                    val call = if (type is IrSimpleType) {
                                        irCall(
                                            symbol,
                                            type
                                        ).also { call ->
                                            call.dispatchReceiver = type.getClass()?.companionObject()?.symbol?.let(::irGetObject)
                                            for ((index, parameter) in type.arguments.withIndex()) {
                                                call.putTypeArgument(index, parameter.typeOrNull ?: continue)
                                                call.putValueArgument(index, serializerExpressionFor(parameter.typeOrNull ?: continue))
                                            }
                                        }
                                    } else {
                                        irCall(
                                            symbol,
                                            type
                                        ).also { call ->
                                            call.dispatchReceiver = type.getClass()?.companionObject()?.symbol?.let(::irGetObject)
                                        }
                                    }

                                    return if (symbol is IrConstructorSymbol) {
                                        irConstructorCall(call, symbol)
                                    } else {
                                        call
                                    }
                                }

                                val additionalSerializer: IrExpression? by lazy {
                                    additionalSerializers[key]?.let {
                                        it.fqNameOrNull()?.let(pluginContext::referenceClass)
                                    }?.let { additionalSerializer ->
                                        if (additionalSerializer.descriptor.kind == ClassKind.OBJECT) {
                                            irGetObject(additionalSerializer)
                                        } else {
                                            // TODO: Use `irSerializerConstructorCall`
                                            irConstructorCall(
                                                irCall(
                                                    additionalSerializer.primaryConstructor
                                                ),
                                                additionalSerializer.primaryConstructor
                                            )
                                        }
                                    }
                                }

                                val companionSerializer: IrExpression? by lazy {
                                    type.getClass()?.let {
                                        val predicate: (IrSimpleFunction) -> Boolean = if (type is IrSimpleType) {
                                            {
                                                it.name.asString() == "serializer" &&
                                                    it.typeParameters.zip(type.arguments).all { (parameter, argument) ->
                                                        parameter.accepts(argument)
                                                    }
                                            }
                                        } else {
                                            { it.name.asString() == "serializer" }
                                        }

                                        it.companionObject()?.functions?.singleOrNull(predicate)
                                    }?.let { companionSerializer ->
                                        irSerializerConstructorCall(companionSerializer.symbol)
                                    }
                                }

                                val builtinSerializer: IrExpression? by lazy {
                                    findStandardKotlinTypeSerializer(pluginContext.moduleDescriptor, kotlinType)?.let {
                                        it.fqNameOrNull()?.let(pluginContext::referenceClass)
                                    }?.let { builtinSerializer ->
                                        if (builtinSerializer.descriptor.kind == ClassKind.OBJECT) {
                                            irGetObject(builtinSerializer)
                                        } else {
                                            irSerializerConstructorCall(builtinSerializer.primaryConstructor)
                                        }
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

                                return additionalSerializer ?: companionSerializer ?: builtinSerializer ?: fallbackSerializer
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
                                                pluginContext.referenceClass(FqName("kotlin.Function1"))!!
                                                    .typeWith(polymorphicModuleBuilderType, pluginContext.irBuiltIns.unitType),
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