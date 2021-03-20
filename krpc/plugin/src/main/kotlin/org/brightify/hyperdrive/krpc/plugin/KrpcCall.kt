package org.brightify.hyperdrive.krpc.plugin

import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class KrpcCall_(
    val function: IrFunction,
    val transportFunctionName: Name,
    val descriptorName: FqName,
    // val descriptorParameters: List<IrType>,
    val expectedErrors: List<IrSimpleType>,
    val requestType: List<IrType>,
    val upstreamFlowType: FlowType?,
    val downstreamFlowType: FlowType?,
    val returnType: IrType,
) {
    data class FlowType(val flow: IrSimpleType, val element: IrType) {
        constructor(flowType: IrSimpleType): this(flowType, flowType.arguments.single().typeOrNull!!)
    }
}

sealed class KrpcCall(val function: IrFunction, val expectedErrors: List<IrSimpleType>, val requestType: List<IrValueParameter>) {
    class SingleCall(
        function: IrFunction,
        expectedErrors: List<IrSimpleType>,
        requestType: List<IrValueParameter>,
        val responseType: IrType
    ): KrpcCall(function, expectedErrors, requestType)

    class ClientStream(
        function: IrFunction,
        expectedErrors: List<IrSimpleType>,
        requestType: List<IrValueParameter>,
        val upstreamFlow: IrValueParameter,
        val responseType: IrType
    ): KrpcCall(function, expectedErrors, requestType)

    class ServerStream(
        function: IrFunction,
        expectedErrors: List<IrSimpleType>,
        requestType: List<IrValueParameter>,
        val downstreamFlow: IrType
    ): KrpcCall(function, expectedErrors, requestType)

    class BiStream(
        function: IrFunction,
        expectedErrors: List<IrSimpleType>,
        requestType: List<IrValueParameter>,
        val upstreamFlow: IrValueParameter,
        val downstreamFlow: IrType
    ): KrpcCall(function, expectedErrors, requestType)
}