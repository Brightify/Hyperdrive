package org.brightify.hyperdrive.multiplatformx

import com.google.auto.service.AutoService
import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.Variance.*
import com.google.devtools.ksp.symbol.impl.kotlin.KSPropertyDeclarationImpl
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.brightify.hyperdrive.swiftbridge.*
import org.brightify.hyperdrive.multiplatformx.util.asTypeName
import org.brightify.hyperdrive.multiplatformx.util.primaryConstructor
import org.jetbrains.kotlin.psi.KtCallExpression

@AutoService(SymbolProcessor::class)
class CoroutineProcessor: SymbolProcessor {
    private lateinit var codeGenerator: CodeGenerator
    private lateinit var logger: KSPLogger

    private companion object {
        val observableDelegates = listOf(
            "published",
            "collected",
        )
    }

    override fun init(options: Map<String, String>, kotlinVersion: KotlinVersion, codeGenerator: CodeGenerator, logger: KSPLogger) {
        this.codeGenerator = codeGenerator
        this.logger = logger
    }

    override fun process(resolver: Resolver) {
        val wrappingHelper = TypeWrappingHelper(resolver)

        resolver.getSymbolsWithAnnotation(ViewModel::class.qualifiedName!!).forEach { symbol ->
            when (symbol) {
                is KSClassDeclaration -> {
                    val (enhancedClassName, enhancedClass) = enhancedClassOf(symbol, wrappingHelper)

                    val fileBuilder = FileSpec.builder(symbol.packageName.asString(), enhancedClassName)
                        .addType(enhancedClass.build())

                    // TODO: Functions/and properties to child presenters -> require wrapping in Swift.
                    // symbol.getDeclaredFunctions()[0].returnType?.resolve().declaration.annotations

                    symbol.getDeclaredProperties().forEach { property ->
                        val impl = property as? KSPropertyDeclarationImpl ?: return@forEach
                        val callExpression = impl.ktProperty.delegateExpression as? KtCallExpression ?: return@forEach
                        // a @Published property
                        if (observableDelegates.any { callExpression.text.startsWith("$it(") }) {
                            val observePropertyName = "observe" + property.simpleName.asString().capitalize()
                            fileBuilder.addProperty(
                                PropertySpec.builder(
                                    observePropertyName,
                                    wrappingHelper.stateFlow(impl.type.asTypeName())
                                )
                                    .receiver(symbol.asStarProjectedType().asTypeName())
                                    .getter(
                                        FunSpec.getterBuilder()
                                            .addStatement("return observe(this::%L).value", property.simpleName.asString())
                                            .build()
                                    )
                                    .build()
                            )

                            val (innerTypeName, innerMapping) = wrappingHelper.resolveInnerWrapping(property.type)
                            val wrapperType = wrappingHelper.stateFlowWrapper(innerTypeName)
                            val mapping: (CodeBlock) -> CodeBlock = { CodeBlock.of("%T.%L", wrapperType.rawType, innerMapping(it)) }

                            enhancedClass.addProperty(
                                PropertySpec.builder(observePropertyName, wrapperType)
                                    .getter(FunSpec.getterBuilder()
                                        .addCode("return %L", mapping(CodeBlock.of("%N.%N", enhancedClass.propertySpecs.first { it.name == "owner" }, observePropertyName)))
                                        .build())
                                    .build()
                            )

                        }
                    }

                    codeGenerator.createNewFile(symbol.packageName.asString(), enhancedClassName).bufferedWriter().use {
                        try {
                            fileBuilder
                                .build()
                                .writeTo(it)
                        } catch (t: Throwable) {
                            logger.error("Error generating for class ${symbol.simpleName.asString()}", symbol)
                            throw t
                        }
                    }
                }
                else -> {

                }
            }
        }

        resolver.getSymbolsWithAnnotation(EnhanceMultiplatform::class.qualifiedName!!).forEach { symbol ->
            when (symbol) {
                is KSClassDeclaration -> {
                    val (enhancedClassName, enhancedClass) = enhancedClassOf(symbol, wrappingHelper)

                    codeGenerator.createNewFile(symbol.packageName.asString(), enhancedClassName).bufferedWriter().use {
                        FileSpec.builder(symbol.packageName.asString(), enhancedClassName)
                            .addType(enhancedClass.build())
                            .build()
                            .writeTo(it)
                    }
                }
            }
        }

        val containers = resolver.getSymbolsWithAnnotation(EnhanceMultiplatform::class.qualifiedName!!).mapNotNull { symbol ->
            when (symbol) {
                is KSClassDeclaration -> {
                    val properties = symbol.getDeclaredProperties().mapNotNull { property ->
                        null
                    }

                    val functions = symbol.getDeclaredFunctions().mapNotNull { function ->
                        val shouldInclude = wrappingHelper.isFlow(function.returnType) || function.modifiers.contains(Modifier.SUSPEND)
                        if (shouldInclude) {
                            val parameters = function.parameters.map { parameter ->
                                Parameter(
                                    name = parameter.name?.getShortName() ?: throw IllegalStateException("Unnamed parameters not supported!"),
                                    type = Type(parameter.type.asTypeName().toString())
                                )
                            }

                            Function(
                                isSuspend = function.modifiers.contains(Modifier.SUSPEND),
                                parameters = parameters,
                                returnType = Type(function.returnType.asTypeName().toString())
                            )
                        } else {
                            null
                        }
                    }

                    Container(
                        name = symbol.simpleName.getShortName(),
                        properties = properties,
                        functions = functions,
                    )
                }
                else -> null
            }
        }

        val metadata = HyperdriveMetadata(
            containers = containers
        )

        codeGenerator.createNewFile("", "hyperdrive.metadata", "").use {
            KLibraryMetadataReaderWriter.write(metadata, it)
        }
    }

    override fun finish() {

    }

    private fun enhancedClassOf(symbol: KSClassDeclaration, wrappingHelper: TypeWrappingHelper): Pair<String, TypeSpec.Builder> {
        val enhancedClassName = "Enhanced" + symbol.simpleName.getShortName()
        val className = ClassName(symbol.packageName.asString(), symbol.simpleName.asString()).let {
            if (symbol.typeParameters.isNotEmpty()) {
                it.parameterizedBy(
                    symbol.typeParameters.map { it.asTypeName() }
                )
            } else {
                it
            }
        }

        val ownerProperty = PropertySpec.builder("owner", className, KModifier.INTERNAL).build()
        val enhancedClass = TypeSpec
            .classBuilder(ClassName(symbol.packageName.asString(), enhancedClassName))
            .addTypeVariables(symbol.typeParameters.map {
                val variance = when (it.variance) {
                    Variance.STAR -> TODO("What to do with star variance?")
                    INVARIANT -> null
                    COVARIANT -> KModifier.OUT
                    CONTRAVARIANT -> KModifier.IN
                }
                return@map TypeVariableName.invoke(it.name.asString(), it.bounds.map { it.asTypeName() }, variance)
            })
            .primaryConstructor(ownerProperty)

        val properties = symbol.getDeclaredProperties().filter { it.getVisibility() != Visibility.PRIVATE }.mapNotNull { property ->
            val (flowWrapper, mapping) = wrappingHelper.resolveWrapping(property.type) ?: return@mapNotNull null

            PropertySpec.builder(property.simpleName.getShortName(), flowWrapper)
                .getter(FunSpec.getterBuilder()
                    .addCode("return %L", mapping(CodeBlock.of("%N.%N", ownerProperty, property.simpleName.asString())))
                    .build())
                .build()
        }

        val functions = symbol.getDeclaredFunctions().filter { it.getVisibility() != Visibility.PRIVATE }.mapNotNull { function ->
            val (flowWrapper, mapping) = wrappingHelper.resolveWrapping(function.returnType) ?: return@mapNotNull null

            val parameters = function.parameters.map { parameter ->
                val type = parameter.type.asTypeName()
                parameter.name?.let {
                    ParameterSpec(it.getShortName(), type)
                } ?: ParameterSpec.Companion.unnamed(type)
            }

            FunSpec.builder(function.simpleName.getShortName())
                .addParameters(parameters)
                .returns(flowWrapper)
                .addCode("return %L", mapping(CodeBlock.of("%N.%N(%L)", ownerProperty, function.simpleName.asString(), parameters.map {
                    CodeBlock.of("%N", it)
                }.joinToCode())))
                .build()
        }

        enhancedClass
            .addProperties(properties)
            .addFunctions(functions)

        return enhancedClassName to enhancedClass
    }
}