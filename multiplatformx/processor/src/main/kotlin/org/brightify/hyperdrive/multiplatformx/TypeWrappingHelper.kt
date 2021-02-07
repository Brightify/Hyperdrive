package org.brightify.hyperdrive.multiplatformx

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.brightify.hyperdrive.multiplatformx.util.asTypeName
import org.brightify.hyperdrive.multiplatformx.util.singleTypeParameter

class TypeWrappingHelper(
    private val resolver: Resolver
) {
    val mutableStateFlowType = resolver.getClassDeclarationByName<MutableStateFlow<*>>() ?: error("Could not resolve MutableStateFlow!")
    val stateFlowType = resolver.getClassDeclarationByName<StateFlow<*>>() ?: error("Could not resolve StateFlow!")
    val flowType = resolver.getClassDeclarationByName<Flow<*>>() ?: error("Could not resolve Flow!")
    val listType = resolver.getClassDeclarationByName<List<*>>() ?: error("Could not resolve List!")

    fun isMutableStateFlow(type: KSTypeReference?): Boolean {
        val resolvedType = type?.resolve() ?: return false
        return mutableStateFlowType.asStarProjectedType().isAssignableFrom(resolvedType)
    }

    fun isStateFlow(type: KSTypeReference?): Boolean {
        val resolvedType = type?.resolve() ?: return false
        return stateFlowType.asStarProjectedType().isAssignableFrom(resolvedType)
    }

    fun isFlow(type: KSTypeReference?): Boolean {
        val resolvedType = type?.resolve() ?: return false
        return flowType.asStarProjectedType().isAssignableFrom(resolvedType)
    }

    fun isList(type: KSTypeReference?): Boolean {
        val resolvedType = type?.resolve() ?: return false
        return listType.asStarProjectedType().isAssignableFrom(resolvedType)
    }

    fun resolveWrapping(type: KSTypeReference?): Pair<TypeName, (CodeBlock) -> CodeBlock>? {
        val (outerTypeName, innerMapping) = when {
            isMutableStateFlow(type) -> {
                val (innerTypeName, innerMapping) = resolveInnerWrapping(type.singleTypeParameter().type)
                mutableStateFlowWrapper(innerTypeName) to innerMapping
            }
            isStateFlow(type) -> {
                val (innerTypeName, innerMapping) = resolveInnerWrapping(type.singleTypeParameter().type)
                stateFlowWrapper(innerTypeName) to innerMapping
            }
            isFlow(type) -> {
                val (innerTypeName, innerMapping) = resolveInnerWrapping(type.singleTypeParameter().type)
                flowWrapper(innerTypeName) to innerMapping
            }
            else -> return null
        }

        return outerTypeName to {
            CodeBlock.of("%T.%L", outerTypeName.rawType, innerMapping(it))
        }
    }

    fun resolveInnerWrapping(type: KSTypeReference?): Pair<TypeName, (CodeBlock) -> CodeBlock> = when {
        isList(type) -> {
            val innerType = type.singleTypeParameter().type.asTypeName()
            listWrapper(innerType) to {
                if (innerType.isNullable) {
                    CodeBlock.of("wrapNullableList(%L)", it)
                } else {
                    CodeBlock.of("wrapNonNullList(%L)", it)
                }
            }
        }
        else -> type.asTypeName() to {
            CodeBlock.of("wrap(%L)", it)
        }
    }

    fun stateFlow(type: TypeName) = ClassName(stateFlowType.packageName.asString(), stateFlowType.simpleName.asString()).parameterizedBy(type)

    fun mutableStateFlowWrapper(type: TypeName) = if (type.isNullable) {
        nullableMutableStateFlowWrapper(type.copy(nullable = false))
    } else {
        nonNullMutableStateFlowWrapper(type)
    }

    private fun nullableMutableStateFlowWrapper(type: TypeName) =
        ClassName("org.brightify.hyperdrive.multiplatformx", "NullableMutableStateFlowWrapper").parameterizedBy(type)

    private fun nonNullMutableStateFlowWrapper(type: TypeName) =
        ClassName("org.brightify.hyperdrive.multiplatformx", "NonNullMutableStateFlowWrapper").parameterizedBy(type)

    fun stateFlowWrapper(type: TypeName) = if (type.isNullable) {
        nullableStateFlowWrapper(type.copy(nullable = false))
    } else {
        nonNullStateFlowWrapper(type)
    }

    private val flowMapMember = MemberName("kotlinx.coroutines.flow", "map")

    private fun nullableStateFlowWrapper(type: TypeName) =
        ClassName("org.brightify.hyperdrive.multiplatformx", "NullableStateFlowWrapper").parameterizedBy(type)

    private fun nonNullStateFlowWrapper(type: TypeName) =
        ClassName("org.brightify.hyperdrive.multiplatformx", "NonNullStateFlowWrapper").parameterizedBy(type)

    fun flowWrapper(type: TypeName) = if (type.isNullable) {
        nullableFlowWrapper(type.copy(nullable = false))
    } else {
        nonNullFlowWrapper(type)
    }

    private fun nullableFlowWrapper(type: TypeName) =
        ClassName("org.brightify.hyperdrive.multiplatformx", "NullableFlowWrapper").parameterizedBy(type)

    private fun nonNullFlowWrapper(type: TypeName) =
        ClassName("org.brightify.hyperdrive.multiplatformx", "NonNullFlowWrapper").parameterizedBy(type)

    private fun listWrapper(type: TypeName) = if (type.isNullable) {
        ClassName("org.brightify.hyperdrive.multiplatformx", "NullableListWrapper").parameterizedBy(type.copy(nullable = false))
    } else {
        ClassName("org.brightify.hyperdrive.multiplatformx", "NonNullListWrapper").parameterizedBy(type)
    }
}