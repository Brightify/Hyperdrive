package org.brightify.hyperdrive.multiplatformx.util

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName

fun KSType.asTypeName(): TypeName {
    val declaration = declaration
    if (declaration is KSTypeParameter) {
        return TypeVariableName(declaration.name.asString())
    }

    val className = declaration.qualifiedName?.asString()?.let(ClassName::bestGuess)
        ?: ClassName(declaration.packageName.asString(), declaration.simpleName.asString())

    return if (arguments.isEmpty()) {
        className
    } else {
        className.parameterizedBy(arguments.map { it.type.asTypeName() })
    }.copy(nullable = isMarkedNullable)
}

fun KSTypeReference?.asTypeName(): TypeName {
    val resolved = this!!.resolve()!!

    return resolved.asTypeName()
}

fun KSTypeParameter.asTypeName(): TypeName {
    return ClassName(packageName.asString(), simpleName.asString())
}