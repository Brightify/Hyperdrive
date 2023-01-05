package org.brightify.hyperdrive.krpc.plugin

import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class KrpcCall(
    val function: IrFunction,
    val transportFunctionName: Name,
    val descriptorName: FqName,
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