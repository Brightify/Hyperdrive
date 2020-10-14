package org.brightify.hyperdrive.krpc

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlinx.coroutines.flow.Flow
import org.brightify.hyperdrive.krpc.api.Error
import org.brightify.hyperdrive.krpc.api.Service
import org.jetbrains.kotlin.ksp.getClassDeclarationByName
import org.jetbrains.kotlin.ksp.getDeclaredFunctions
import org.jetbrains.kotlin.ksp.processing.CodeGenerator
import org.jetbrains.kotlin.ksp.processing.KSPLogger
import org.jetbrains.kotlin.ksp.processing.Resolver
import org.jetbrains.kotlin.ksp.processing.SymbolProcessor
import org.jetbrains.kotlin.ksp.symbol.KSClassDeclaration
import org.jetbrains.kotlin.ksp.symbol.KSFunctionDeclaration
import org.jetbrains.kotlin.ksp.symbol.KSType
import org.jetbrains.kotlin.ksp.symbol.KSTypeArgument
import org.jetbrains.kotlin.ksp.symbol.KSTypeReference
import org.jetbrains.kotlin.ksp.symbol.KSVariableParameter
import org.jetbrains.kotlin.ksp.symbol.Modifier
import kotlin.reflect.KClass

fun KSTypeReference?.asTypeName(): TypeName {
    val resolved = this!!.resolve()!!
    val className = ClassName(resolved.declaration.packageName.asString(), resolved.declaration.simpleName.asString())
    return if (resolved.arguments.isEmpty()) {
        className
    } else {
        className.parameterizedBy(resolved.arguments.map { it.type.asTypeName() })
    }
}

fun KSTypeReference?.singleTypeParameter(): KSTypeArgument {
    val resolved = this!!.resolve()!!
    if (resolved.arguments.count() == 1) {
        return resolved.arguments[0]
    } else {
        error("Type $resolved doesn't contain exactly one type argument but: ${resolved.arguments.count()}.")
    }
}

object KnownType {
    object Hyperdrive {
        val serviceDescriptor = ClassName("org.brightify.hyperdrive.krpc.api", "ServiceDescriptor")

        val serviceDescription = ClassName("org.brightify.hyperdrive.krpc.api", "ServiceDescription")

        val rpcErrorSerializer = ClassName("org.brightify.hyperdrive.krpc.api.error", "RPCErrorSerializer")

        fun clientCallDescriptor(request: TypeName, response: TypeName) =
            ClassName("org.brightify.hyperdrive.krpc.api", "ClientCallDescriptor").parameterizedBy(request, response)

        fun coldUpstreamCallDescriptor(request: TypeName, clientStream: TypeName, response: TypeName) =
            ClassName("org.brightify.hyperdrive.krpc.api", "ColdUpstreamCallDescriptor").parameterizedBy(request, clientStream, response)

        fun coldDownstreamCallDescriptor(request: TypeName, serverStream: TypeName) =
            ClassName("org.brightify.hyperdrive.krpc.api", "ColdDownstreamCallDescriptor").parameterizedBy(request, serverStream)

        fun coldBistreamCallDescriptor(request: TypeName, clientStream: TypeName, serverStream: TypeName) =
            ClassName("org.brightify.hyperdrive.krpc.api", "ColdBistreamCallDescriptor").parameterizedBy(request, clientStream, serverStream)

        val serviceCallIdentifier = ClassName("org.brightify.hyperdrive.krpc.api", "ServiceCallIdentifier")

        val serviceClient = ClassName("org.brightify.hyperdrive.client.api", "ServiceClient")

        fun dataWrapper(parameterCount: Int): ClassName =
            ClassName("org.brightify.hyperdrive.krpc.api", "RPCDataWrapper$parameterCount")
    }

    object Serialization {
        val builtinSerializer = MemberName("kotlinx.serialization.builtins", "serializer")

        val serializer = MemberName("kotlinx.serialization", "serializer")
    }
}

sealed class Call(val method: KSFunctionDeclaration) {
    open fun clientMethodBuilder(descriptor: TypeName): FunSpec.Builder = FunSpec.builder(method.simpleName.asString())
        .addModifiers(KModifier.SUSPEND)
        .addModifiers(KModifier.OVERRIDE)

    abstract fun serverCallDescriptor(resolver: Resolver): PropertySpec.Builder

    abstract fun serverCall(): CodeBlock

    protected fun buildRPCErrorSerializer(resolver: Resolver): CodeBlock {
        val errorAnnotationType = resolver.getClassDeclarationByName<Error>()!!
        val errors = (method.annotations + method.parameters.flatMap { it.annotations + it.type.let { it?.annotations ?: emptyList() } } + method.let { it.returnType?.annotations ?: emptyList() })
            .filter { errorAnnotationType.asStarProjectedType().isAssignableFrom(it.annotationType.resolve()!!)  }
            .flatMap {
                it.arguments.flatMap {
                    (it.value as List<KSType>).map {
                        CodeBlock.of("subclass($it::class, $it.serializer())")
                    }
                }
            }

        return CodeBlock.of("%T(%L)", KnownType.Hyperdrive.rpcErrorSerializer, buildCodeBlock {
            beginControlFlow("")
            for (error in errors) {
                add(error)
            }
            endControlFlow()
        })
    }

    class SingleCall(
        method: KSFunctionDeclaration,
        private val requestType: List<KSVariableParameter>,
        private val responseType: KSTypeReference
    ): Call(method) {

        private val request: TypeName = if (requestType.isEmpty()) {
            ClassName("kotlin", "Unit")
        } else {
            KnownType.Hyperdrive.dataWrapper(requestType.count()).parameterizedBy(requestType.map { it.type.asTypeName() })
        }

        private val response: TypeName = responseType.asTypeName()

        override fun clientMethodBuilder(descriptor: TypeName): FunSpec.Builder = super.clientMethodBuilder(descriptor)
                .addParameters(
                    requestType.map {
                        ParameterSpec
                            .builder(it.name!!.getShortName(), it.type.asTypeName())
                            .build()
                    }
                )
                .returns(responseType.asTypeName())
                .addCode(buildCodeBlock {
                    addStatement("return serviceClient.singleCall(%L)", buildCodeBlock {
                        add("%T.Call.%L, ", descriptor, method.simpleName.getShortName())
                        add("%T(%L)", KnownType.Hyperdrive.dataWrapper(requestType.count()), requestType.map {
                            CodeBlock.of(it.name!!.getShortName())
                        }.joinToCode())
                    })
                })

        override fun serverCallDescriptor(resolver: Resolver): PropertySpec.Builder {
            val descriptorType = KnownType.Hyperdrive.clientCallDescriptor(request, response)
            return PropertySpec
                .builder(
                    method.simpleName.getShortName(),
                    descriptorType
                )
                .initializer(
                    buildCodeBlock {
                        addStatement("%T(%L)", descriptorType, buildCodeBlock {
                            add("%T(serviceIdentifier, %S),", KnownType.Hyperdrive.serviceCallIdentifier, method.simpleName.getShortName())
                            add("%M(),", KnownType.Serialization.serializer)
                            add("%M(),", KnownType.Serialization.serializer)
                            add(buildRPCErrorSerializer(resolver))
                        })
                    }
                )
        }

        override fun serverCall(): CodeBlock = buildCodeBlock {
            val namedParams = (0 until requestType.count()).map {
                CodeBlock.of("p$it")
            }
            val closureParams = if (namedParams.isEmpty()) {
                CodeBlock.of("")
            } else {
                CodeBlock.of("(%L) ->", namedParams.joinToCode())
            }
            beginControlFlow("Call.${method.simpleName.getShortName()}.calling { %L", closureParams)
            addStatement("service.%L(%L)", method.simpleName.getShortName(), namedParams.joinToCode())
            endControlFlow()
        }
    }

    class ClientStream(method: KSFunctionDeclaration, val requestType: List<KSVariableParameter>, val upstreamFlow: KSVariableParameter, val responseType: KSTypeReference): Call(method) {
        private val request: TypeName = if (requestType.isEmpty()) {
            ClassName("kotlin", "Unit")
        } else {
            KnownType.Hyperdrive.dataWrapper(requestType.count()).parameterizedBy(requestType.map { it.type.asTypeName() })
        }

        private val response: TypeName = responseType.asTypeName()

        override fun clientMethodBuilder(descriptor: TypeName): FunSpec.Builder = super.clientMethodBuilder(descriptor)
            .addParameters(
                requestType.map {
                    ParameterSpec
                        .builder(it.name!!.getShortName(), it.type.asTypeName())
                        .build()
                }
            )
            .addParameter(
                ParameterSpec
                    .builder(upstreamFlow.name!!.getShortName(), upstreamFlow.type.asTypeName())
                    .build()
            )
            .returns(responseType.asTypeName())
            .addCode(buildCodeBlock {
                addStatement("return serviceClient.clientStream(%L)", buildCodeBlock {
                    add("%T.Call.%L, ", descriptor, method.simpleName.getShortName())
                    add("%T(%L), ", KnownType.Hyperdrive.dataWrapper(requestType.count()), requestType.map {
                        CodeBlock.of(it.name!!.getShortName())
                    }.joinToCode())
                    add("stream")
                })
            })

        override fun serverCallDescriptor(resolver: Resolver): PropertySpec.Builder {
            val descriptorType = KnownType.Hyperdrive.coldUpstreamCallDescriptor(request, upstreamFlow.type.singleTypeParameter().type.asTypeName(), response)
            return PropertySpec
                .builder(
                    method.simpleName.getShortName(),
                    descriptorType
                )
                .initializer(
                    buildCodeBlock {
                        addStatement("%T(%L)", descriptorType, buildCodeBlock {
                            add("%T(serviceIdentifier, %S),", KnownType.Hyperdrive.serviceCallIdentifier, method.simpleName.getShortName())
                            add("%M(),", KnownType.Serialization.serializer)
                            add("%M(),", KnownType.Serialization.serializer)
                            add("%M(),", KnownType.Serialization.serializer)
                            add(buildRPCErrorSerializer(resolver))
                        })
                    }
                )
        }

        override fun serverCall(): CodeBlock = buildCodeBlock {
            beginControlFlow("Call.${method.simpleName.getShortName()}.calling { request, stream ->")
            val namedParams = (0 until requestType.count()).map {
                CodeBlock.of("p$it")
            }
            if (namedParams.isNotEmpty()) {
                addStatement("val (%L) = request", namedParams.joinToCode())
            }

            addStatement("service.%L(%L)", method.simpleName.getShortName(), (namedParams + CodeBlock.of("stream")).joinToCode())
            endControlFlow()
        }
    }

    class ServerStream(method: KSFunctionDeclaration, val requestType: List<KSVariableParameter>, val downstreamFlow: KSTypeReference): Call(method) {
        private val request: TypeName = if (requestType.isEmpty()) {
            ClassName("kotlin", "Unit")
        } else {
            KnownType.Hyperdrive.dataWrapper(requestType.count()).parameterizedBy(requestType.map { it.type.asTypeName() })
        }

        override fun clientMethodBuilder(descriptor: TypeName): FunSpec.Builder = super.clientMethodBuilder(descriptor)
            .addParameters(
                requestType.map {
                    ParameterSpec
                        .builder(it.name!!.getShortName(), it.type.asTypeName())
                        .build()
                }
            )
            .returns(downstreamFlow.asTypeName())
            .addCode(buildCodeBlock {
                addStatement("return serviceClient.serverStream(%L)", buildCodeBlock {
                    add("%T.Call.%L, ", descriptor, method.simpleName.getShortName())
                    add("%T(%L)", KnownType.Hyperdrive.dataWrapper(requestType.count()), requestType.map {
                        CodeBlock.of(it.name!!.getShortName())
                    }.joinToCode())
                })
            })

        override fun serverCallDescriptor(resolver: Resolver): PropertySpec.Builder {
            val descriptorType = KnownType.Hyperdrive.coldDownstreamCallDescriptor(request, downstreamFlow.singleTypeParameter().type.asTypeName())
            return PropertySpec
                .builder(
                    method.simpleName.getShortName(),
                    descriptorType
                )
                .initializer(
                    buildCodeBlock {
                        addStatement("%T(%L)", descriptorType, buildCodeBlock {
                            add("%T(serviceIdentifier, %S),", KnownType.Hyperdrive.serviceCallIdentifier, method.simpleName.getShortName())
                            add("%M(),", KnownType.Serialization.serializer)
                            add("%M(),", KnownType.Serialization.serializer)
                            add(buildRPCErrorSerializer(resolver))
                        })
                    }
                )
        }

        override fun serverCall(): CodeBlock = buildCodeBlock {
            val namedParams = (0 until requestType.count()).map {
                CodeBlock.of("p$it")
            }
            val closureParams = if (namedParams.isEmpty()) {
                CodeBlock.of("")
            } else {
                CodeBlock.of("(%L) ->", namedParams.joinToCode())
            }
            beginControlFlow("Call.${method.simpleName.getShortName()}.calling { %L", closureParams)
            addStatement("service.%L(%L)", method.simpleName.getShortName(), namedParams.joinToCode())
            endControlFlow()
        }
    }

    class BiStream(method: KSFunctionDeclaration, val requestType: List<KSVariableParameter>, val upstreamFlow: KSVariableParameter, val downstreamFlow: KSTypeReference): Call(method) {
        private val request: TypeName = if (requestType.isEmpty()) {
            ClassName("kotlin", "Unit")
        } else {
            KnownType.Hyperdrive.dataWrapper(requestType.count()).parameterizedBy(requestType.map { it.type.asTypeName() })
        }

        override fun clientMethodBuilder(descriptor: TypeName): FunSpec.Builder = super.clientMethodBuilder(descriptor)
            .addParameter(
                ParameterSpec
                    .builder(upstreamFlow.name!!.getShortName(), upstreamFlow.type.asTypeName())
                    .build()
            )
            .returns(downstreamFlow.asTypeName())
            .addCode(buildCodeBlock {
                addStatement("return serviceClient.biStream(%L)", buildCodeBlock {
                    add("%T.Call.%L, ", descriptor, method.simpleName.getShortName())
                    add("%T(%L), ", KnownType.Hyperdrive.dataWrapper(requestType.count()), requestType.map {
                        CodeBlock.of(it.name!!.getShortName())
                    }.joinToCode())
                    add("stream")
                })
            })

        override fun serverCallDescriptor(resolver: Resolver): PropertySpec.Builder {
            val descriptorType = KnownType.Hyperdrive.coldBistreamCallDescriptor(request, upstreamFlow.type.singleTypeParameter().type.asTypeName(), downstreamFlow.singleTypeParameter().type.asTypeName())
            return PropertySpec
                .builder(
                    method.simpleName.getShortName(),
                    descriptorType
                )
                .initializer(
                    buildCodeBlock {
                        addStatement("%T(%L)", descriptorType, buildCodeBlock {
                            add("%T(serviceIdentifier, %S),", KnownType.Hyperdrive.serviceCallIdentifier, method.simpleName.getShortName())
                            add("%M(),", KnownType.Serialization.serializer)
                            add("%M(),", KnownType.Serialization.serializer)
                            add("%M(),", KnownType.Serialization.serializer)
                            add(buildRPCErrorSerializer(resolver))
                        })
                    }
                )
        }

        override fun serverCall(): CodeBlock = buildCodeBlock {
            beginControlFlow("Call.${method.simpleName.getShortName()}.calling { request, stream ->")
            val namedParams = (0 until requestType.count()).map {
                CodeBlock.of("p$it")
            }
            if (namedParams.isNotEmpty()) {
                addStatement("val (%L) = request", namedParams.joinToCode())
            }
            addStatement("service.%L(%L)", method.simpleName.getShortName(), (namedParams + CodeBlock.of("stream")).joinToCode())
            endControlFlow()
        }
    }
}

@AutoService(SymbolProcessor::class)
class ServiceProcessor: SymbolProcessor {
    private lateinit var codeGenerator: CodeGenerator
    private lateinit var logger: KSPLogger

    override fun init(
        options: Map<String, String>,
        kotlinVersion: KotlinVersion,
        codeGenerator: CodeGenerator,
        logger: KSPLogger
    ) {
        this.codeGenerator = codeGenerator
        this.logger = logger
    }

    override fun process(resolver: Resolver) {
        resolver.getSymbolsWithAnnotation(Service::class.qualifiedName!!).forEach { symbol ->
            when {
                symbol is KSClassDeclaration -> {
                    val serviceName = ClassName(symbol.packageName.asString(), symbol.simpleName.asString())
                    val clientName = "${symbol.simpleName.asString()}Client"
                    val clientBuilder = TypeSpec.classBuilder(clientName)
                        .addSuperinterface(serviceName)
                        .primaryConstructor(
                            PropertySpec.builder(
                                "serviceClient",
                                KnownType.Hyperdrive.serviceClient,
                                KModifier.PRIVATE
                            ).build()
                        )

                    val descriptorCompanionBuilder = TypeSpec.objectBuilder("Call")

                    val descriptorName = ClassName(symbol.packageName.asString(), "${symbol.simpleName.asString()}Descriptor")
                    val descriptorBuilder = TypeSpec.objectBuilder(descriptorName)
                        .addSuperinterface(
                            KnownType.Hyperdrive.serviceDescriptor.parameterizedBy(serviceName)
                        )
                        .addProperty(
                            PropertySpec.builder("serviceIdentifier", String::class)
                                .addModifiers(KModifier.CONST)
                                .initializer("\"${serviceName.canonicalName}\"")
                                .build()
                        )

                    val flowType = resolver.getClassDeclarationByName<Flow<*>>()!!

                    fun isFlow(type: KSTypeReference): Boolean {
                        return flowType.asStarProjectedType().isAssignableFrom(type.resolve()!!)
                    }

                    val calls = symbol.getDeclaredFunctions().mapNotNull { method ->
                        if (!method.modifiers.contains(Modifier.SUSPEND)) {
                            logger.error("Only suspending methods are supported!", method)
                            return@mapNotNull null
                        }

                        val (clientRequestParameters: List<KSVariableParameter>, clientStreamingFlow: KSVariableParameter?) = if (1 == 1) {
                            val lastParam = method.parameters.lastOrNull()
                            val lastParamType = lastParam?.type
                            if (lastParamType != null && isFlow(lastParamType)) {
                                method.parameters.dropLast(1) to lastParam
                            } else {
                                method.parameters to null
                            }
                        } else {
                            method.parameters to null
                        }

                        val returnType = method.returnType

                        return@mapNotNull when {
                            clientStreamingFlow != null && isFlow(returnType!!) -> {
                                Call.BiStream(method, clientRequestParameters, clientStreamingFlow, returnType)
                            }
                            clientStreamingFlow != null && returnType != null -> {
                                Call.ClientStream(method, clientRequestParameters, clientStreamingFlow, returnType)
                            }
                            isFlow(returnType!!) -> {
                                Call.ServerStream(method, clientRequestParameters, returnType)
                            }
                            returnType != null -> {
                                Call.SingleCall(method, clientRequestParameters, returnType)
                            }
                            else -> {
                                logger.error("Unknown input / output parameters!", method)
                                return
                            }
                        }
                    }

                    clientBuilder.addFunctions(
                        calls.map { call ->
                            call.clientMethodBuilder(descriptorName).build()
                        }
                    )

                    descriptorCompanionBuilder.addProperties(
                        calls.map { call ->
                            call.serverCallDescriptor(resolver).build()
                        }
                    )

                    val describeService = FunSpec.builder("describe")
                        .addModifiers(KModifier.OVERRIDE)
                        .addParameter("service", serviceName)
                        .returns(KnownType.Hyperdrive.serviceDescription)
                        .addCode(buildCodeBlock {
                            addStatement("return %T(%L)", KnownType.Hyperdrive.serviceDescription, buildCodeBlock {
                                add("serviceIdentifier,")
                                add("listOf(%L)",
                                    calls.map { call ->
                                        call.serverCall()
                                    }.joinToCode()
                                )
                            })
                        })
                        .build()

                    val client = clientBuilder.build()
                    val descriptor = descriptorBuilder
                        .addType(
                            descriptorCompanionBuilder.build()
                        )
                        .addFunction(describeService)
                        .build()

                    codeGenerator.createNewFile(symbol.packageName.asString(), clientName).bufferedWriter().use {
                        FileSpec.builder(symbol.packageName.asString(), clientName)
                            .addType(client)
                            .build()
                            .writeTo(it)
                    }

                    codeGenerator.createNewFile(symbol.packageName.asString(), descriptorName.simpleName).bufferedWriter().use {
                        FileSpec.builder(symbol.packageName.asString(), descriptorName.simpleName)
                            .addType(descriptor)
                            .build()
                            .writeTo(it)
                    }
                }
            }
        }
        logger.info("process")
    }

    override fun finish() {
        logger.info("did finish")
    }
}

fun TypeSpec.Builder.primaryConstructor(vararg properties: PropertySpec): TypeSpec.Builder {
    val propertySpecs = properties.map { p -> p.toBuilder().initializer(p.name).build() }
    val parameters = propertySpecs.map { ParameterSpec.builder(it.name, it.type).build() }
    val constructor = FunSpec.constructorBuilder()
        .addParameters(parameters)
        .build()

    return this
        .primaryConstructor(constructor)
        .addProperties(propertySpecs)
}