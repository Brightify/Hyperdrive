package org.brightify.hyperdrive.krpc.util

import org.jetbrains.kotlin.ksp.symbol.KSTypeArgument
import org.jetbrains.kotlin.ksp.symbol.KSTypeReference

fun KSTypeReference?.singleTypeParameter(): KSTypeArgument {
    val resolved = this!!.resolve()!!
    if (resolved.arguments.count() == 1) {
        return resolved.arguments[0]
    } else {
        error("Type $resolved doesn't contain exactly one type argument but: ${resolved.arguments.count()}.")
    }
}