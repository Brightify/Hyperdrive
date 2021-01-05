package org.brightify.hyperdrive.krpc

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.joinToCode
import kotlinx.coroutines.flow.Flow
import org.brightify.hyperdrive.krpc.api.Service
import org.brightify.hyperdrive.krpc.util.primaryConstructor
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier

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

                        val (clientRequestParameters: List<KSValueParameter>, clientStreamingFlow: KSValueParameter?) = if (1 == 1) {
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

                    codeGenerator.createNewFile(Dependencies.ALL_FILES, symbol.packageName.asString(), clientName).bufferedWriter().use {
                        FileSpec.builder(symbol.packageName.asString(), clientName)
                            .addType(client)
                            .build()
                            .writeTo(it)
                    }

                    codeGenerator.createNewFile(Dependencies.ALL_FILES, symbol.packageName.asString(), descriptorName.simpleName).bufferedWriter().use {
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
