package org.brightify.hyperdrive.krpc.plugin.ir.util

import org.brightify.hyperdrive.krpc.plugin.KnownType
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.isSubtypeOf
import org.jetbrains.kotlin.ir.types.toKotlinType
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.irConstructorCall
import org.jetbrains.kotlin.js.translate.utils.AnnotationsUtils
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.descriptorUtil.firstArgument
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.representativeUpperBound
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlinx.serialization.compiler.backend.common.findStandardKotlinTypeSerializer

@OptIn(ObsoleteDescriptorBasedAPI::class)
class SerializerResolver(
    override val pluginContext: IrPluginContext,
    private val service: IrClass,
): PluginContextExtension {

    val additionalSerializers: Map<Pair<ClassDescriptor, Boolean>, ClassDescriptor> by lazy {
        fun getKClassListFromFileAnnotation(annotationFqName: FqName, declarationInFile: DeclarationDescriptor): List<KotlinType> {
            val annotation = AnnotationsUtils.getContainingFileAnnotations(pluginContext.bindingContext, declarationInFile)
                .find { it.fqName == annotationFqName } ?: return emptyList()

            @Suppress("UNCHECKED_CAST")
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

    val serializer: IrSimpleFunctionSymbol by lazy {
        KnownType.Serialization.serializer.asFunction {

            it.owner.valueParameters.isEmpty() &&
                it.owner.typeParameters.count() == 1 &&
                it.owner.dispatchReceiverParameter == null &&
                it.owner.extensionReceiverParameter == null
        }
    }

    fun resolveSerializer(builder: IrBuilderWithScope, type: IrType): IrExpression = with(builder) {
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
                        call.putValueArgument(index, resolveSerializer(this, parameter.typeOrNull ?: continue))
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
                            it.typeParameters.count() == type.arguments.count() &&
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

    private fun toClassDescriptor(type: KotlinType?): ClassDescriptor? {
        return type?.constructor?.declarationDescriptor?.let { descriptor ->
            when(descriptor) {
                is ClassDescriptor -> descriptor
                is TypeParameterDescriptor -> toClassDescriptor(descriptor.representativeUpperBound)
                else -> null
            }
        }
    }

    private fun IrTypeParameter.accepts(argument: IrTypeArgument): Boolean {
        val argumentType = argument.typeOrNull ?: return true
        return superTypes.all {
            argumentType.isSubtypeOf(it, pluginContext.irBuiltIns)
        }
    }
}