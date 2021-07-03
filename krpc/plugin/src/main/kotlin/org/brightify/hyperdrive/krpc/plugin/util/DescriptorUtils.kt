package org.brightify.hyperdrive.krpc.plugin.util

import org.brightify.hyperdrive.krpc.plugin.KnownType
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.parentClassOrNull

val ClassDescriptor.containingClass: ClassDescriptor?
    get() = containingDeclaration as? ClassDescriptor

val ClassDescriptor.isContainingClassKrpcEnabled: Boolean
    get() = containingClass?.isKrpcEnabled ?: false

val ClassDescriptor.isKrpcEnabled: Boolean
    get() = annotations.hasAnnotation(KnownType.Annotation.enableKrpc)

val ClassDescriptor.isKrpcClient: Boolean
    get() = name == KnownType.Nested.client && isContainingClassKrpcEnabled

val ClassDescriptor.isKrpcDescriptor: Boolean
    get() = name == KnownType.Nested.descriptor && isContainingClassKrpcEnabled

val ClassDescriptor.isKrpcDescriptorCall: Boolean
    get() = name == KnownType.Nested.call && containingClass?.isKrpcDescriptor ?: false

val IrClass.isParentKrpcEnabled: Boolean
    get() = parentClassOrNull?.isKrpcEnabled ?: false

val IrClass.isKrpcEnabled: Boolean
    get() = annotations.hasAnnotation(KnownType.Annotation.enableKrpc)

val IrClass.isKrpcClient: Boolean
    get() = name == KnownType.Nested.client && isParentKrpcEnabled

val IrClass.isKrpcDescriptor: Boolean
    get() = name == KnownType.Nested.descriptor && isParentKrpcEnabled

val IrClass.isKrpcDescriptorCall: Boolean
    get() = name == KnownType.Nested.call && parentClassOrNull?.isKrpcDescriptor ?: false
