package org.brightify.hyperdrive.krpc.plugin.ir.util

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.name.Name

fun IrClass.property(name: Name): IrProperty =
    properties.single { it.name == name }

val IrClassSymbol.primaryConstructor: IrConstructorSymbol
    get() = constructors.first { it.owner.isPrimary }