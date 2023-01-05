package org.brightify.hyperdrive.krpc.plugin

import org.brightify.hyperdrive.krpc.plugin.ir.util.PluginContextExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

abstract class KrpcIrElementTransformerVoidBase: IrElementTransformerVoid(), PluginContextExtension {

    abstract override val pluginContext: IrPluginContext
    protected abstract val messageCollector: MessageCollector

    protected val flowType by lazy { pluginContext.referenceClass(KnownType.Coroutines.flow)!! }

    protected fun getCalls(irClass: IrClass): Map<Name, KrpcCall> {
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
                    KrpcCall(
                        function,
                        Name.identifier("biStream"),
                        KnownType.API.coldBistreamCallDescription,
                        expectedErrors,
                        clientRequestParameters.map { it.type },
                        KrpcCall.FlowType(clientStreamingFlow),
                        KrpcCall.FlowType(returnType),
                        returnType,
                    )
                }
                clientStreamingFlow != null -> {
                    KrpcCall(
                        function,
                        Name.identifier("clientStream"),
                        KnownType.API.coldUpstreamCallDescription,
                        expectedErrors,
                        clientRequestParameters.map { it.type },
                        KrpcCall.FlowType(clientStreamingFlow),
                        null,
                        returnType
                    )
                }
                isFlow(returnType) -> {
                    KrpcCall(
                        function,
                        Name.identifier("serverStream"),
                        KnownType.API.coldDownstreamCallDescription,
                        expectedErrors,
                        clientRequestParameters.map { it.type },
                        null,
                        KrpcCall.FlowType(returnType),
                        returnType
                    )
                }
                else -> {
                    KrpcCall(
                        function,
                        Name.identifier("singleCall"),
                        KnownType.API.singleCallDescription,
                        expectedErrors,
                        clientRequestParameters.map { it.type },
                        null,
                        null,
                        returnType
                    )
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