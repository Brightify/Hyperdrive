package org.brightify.hyperdrive.autofactory

import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

class AutoFactoryFakeOverrideTransformer(val anyClass: IrClassSymbol): IrElementTransformerVoid() {

   @OptIn(ObsoleteDescriptorBasedAPI::class)
   override fun visitFunction(declaration: IrFunction): IrStatement {
       return if (anyClass.getSimpleFunction(declaration.name.asString()) != null) {
           declaration.factory.buildFun {
               updateFrom(declaration)
               name = declaration.name
               returnType = declaration.returnType
               origin = IrDeclarationOrigin.FAKE_OVERRIDE
               isFakeOverride = true
           }.also {
               it.parent = declaration.parent
               it.annotations = declaration.annotations.map { p -> p.deepCopyWithSymbols(it) }
               it.typeParameters = declaration.typeParameters.map { p -> p.deepCopyWithSymbols(it) }
               it.dispatchReceiverParameter = declaration.dispatchReceiverParameter?.deepCopyWithSymbols(it)
               it.extensionReceiverParameter = declaration.extensionReceiverParameter?.deepCopyWithSymbols(it)
               it.valueParameters = declaration.valueParameters.map { p -> p.deepCopyWithSymbols(it) }
               it.contextReceiverParametersCount = declaration.contextReceiverParametersCount
               it.metadata = declaration.metadata
               it.overriddenSymbols = listOf(declaration.symbol as IrSimpleFunctionSymbol)
               it.attributeOwnerId = it
           }
           // IrFakeOverrideFunctionImpl(
           //     startOffset = declaration.startOffset,
           //     endOffset = declaration.endOffset,
           //     origin = IrDeclarationOrigin.FAKE_OVERRIDE,
           //     name = declaration.name,
           //     visibility = declaration.visibility,
           //     modality = declaration.descriptor.modality,
           //     returnType = declaration.returnType,
           //     isInline = declaration.descriptor.isInline,
           //     isExternal = declaration.descriptor.isExternal,
           //     isTailrec = declaration.descriptor.isTailrec,
           //     isSuspend = declaration.descriptor.isSuspend,
           //     isOperator = declaration.descriptor.isOperator,
           //     isInfix = declaration.descriptor.isInfix,
           //     isExpect = declaration.descriptor.isExpect,
           // ).apply {
           //     parent = declaration.parent
           //
           //     acquireSymbol(IrSimpleFunctionSymbolImpl())
           // }
       } else {
           super.visitFunction(declaration)
       }
   }
}
