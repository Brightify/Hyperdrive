package org.brightify.hyperdrive.krpc.util

import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeReference

fun KSTypeReference?.singleTypeParameter(): KSTypeArgument {
    val resolved = this!!.resolve()!!
    if (resolved.arguments.count() == 1) {
        return resolved.arguments[0]
    } else {
        error("Type $resolved doesn't contain exactly one type argument but: ${resolved.arguments.count()}.")
    }
}