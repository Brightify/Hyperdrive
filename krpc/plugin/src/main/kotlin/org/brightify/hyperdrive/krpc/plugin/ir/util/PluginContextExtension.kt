package org.brightify.hyperdrive.krpc.plugin.ir.util

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.name.FqName

interface PluginContextExtension {
    val pluginContext: IrPluginContext

    val FqName.primaryConstructor: IrConstructorSymbol
        get() = pluginContext.referenceConstructors(this).single { it.owner.isPrimary }

    fun FqName.asClass(): IrClassSymbol = pluginContext.referenceClass(this)!!

    fun FqName.asFunction(filter: (IrSimpleFunctionSymbol) -> Boolean) = pluginContext.referenceFunctions(this).single(filter)

    fun FqName.asFunctions(): Collection<IrFunctionSymbol> = pluginContext.referenceFunctions(this)
}