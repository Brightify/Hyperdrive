package org.brightify.hyperdrive.krpc.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.isSubtypeOf
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlinx.serialization.compiler.backend.ir.IrBuilderExtension
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationPluginContext
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

abstract class KrpcIrElementTransformerVoidBase: IrElementTransformerVoid(), IrBuilderExtension {

    protected abstract val pluginContext: IrPluginContext
    protected abstract val messageCollector: MessageCollector

    override val compilerContext: SerializationPluginContext
        get() = pluginContext

    protected val flowType by lazy { pluginContext.referenceClass(KnownType.Coroutines.flow)!! }

    protected val FqName.primaryConstructor: IrConstructorSymbol
        get() = pluginContext.referenceConstructors(this).single { it.owner.isPrimary }

    protected val IrClassSymbol.primaryConstructor: IrConstructorSymbol
        get() = this.constructors.first { it.owner.isPrimary }

    protected fun FqName.asClass(): IrClassSymbol = pluginContext.referenceClass(this)!!

    protected fun FqName.asFunction(filter: (IrSimpleFunctionSymbol) -> Boolean) = pluginContext.referenceFunctions(this).single(filter)

    protected fun FqName.asFunctions(): Collection<IrFunctionSymbol> = pluginContext.referenceFunctions(this)

    protected fun IrClass.property(name: Name): IrProperty =
        properties.single { it.name == name }

    protected fun getCalls(irClass: IrClass): Map<Name, KrpcCall_> {
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
    protected fun isFlow(type: IrType): Boolean {
        contract {
            returns(true) implies (type is IrSimpleType)
        }
        return type.isSubtypeOfClass(flowType)
    }

}