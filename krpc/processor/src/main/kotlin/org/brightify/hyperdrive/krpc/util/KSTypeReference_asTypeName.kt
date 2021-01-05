package org.brightify.hyperdrive.krpc.util

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.google.devtools.ksp.symbol.KSTypeReference

fun KSTypeReference?.asTypeName(): TypeName {
    val resolved = this!!.resolve()!!
    val className = ClassName(resolved.declaration.packageName.asString(), resolved.declaration.simpleName.asString())
    return if (resolved.arguments.isEmpty()) {
        className
    } else {
        className.parameterizedBy(resolved.arguments.map { it.type.asTypeName() })
    }
}