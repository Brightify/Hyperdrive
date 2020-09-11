package org.brightify.hyperdrive.krpc

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.brightify.hyperdrive.krpc.api.Service
import org.jetbrains.kotlin.ksp.getClassDeclarationByName
import org.jetbrains.kotlin.ksp.getDeclaredFunctions
import org.jetbrains.kotlin.ksp.processing.CodeGenerator
import org.jetbrains.kotlin.ksp.processing.KSPLogger
import org.jetbrains.kotlin.ksp.processing.Resolver
import org.jetbrains.kotlin.ksp.processing.SymbolProcessor
import org.jetbrains.kotlin.ksp.symbol.KSClassDeclaration
import org.jetbrains.kotlin.ksp.symbol.KSTypeReference
import org.jetbrains.kotlin.ksp.symbol.KSVariableParameter
import org.jetbrains.kotlin.ksp.symbol.Modifier
import java.io.File
import kotlin.math.log

sealed class Call {
    class SingleCall(val requestType: List<KSVariableParameter>, val responseType: KSTypeReference): Call()
    class ClientStream(val upstreamFlow: KSVariableParameter, val responseType: KSTypeReference): Call()
    class ServerStream(val requestType: List<KSVariableParameter>, val downstreamFlow: KSTypeReference): Call()
    class BiStream(val upstreamFlow: KSVariableParameter, val downstreamFlow: KSTypeReference): Call()
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
                    val clientName = "${symbol.simpleName.asString()}Client"
                    val clientBuilder = TypeSpec.classBuilder(clientName)
                        .addSuperinterface(ClassName(symbol.packageName.asString(), symbol.simpleName.asString()))
                        .primaryConstructor(
                            PropertySpec.builder("serviceClient", ClassName("org.brightify.hyperdrive.client.api", "ServiceClient"), KModifier.PRIVATE).build()
                        )

                    val flowType = resolver.getClassDeclarationByName<Flow<*>>()!!

                    fun isFlow(type: KSTypeReference): Boolean {
                        return flowType.asStarProjectedType().isAssignableFrom(type.resolve()!!)
                    }

                    for (method in symbol.getDeclaredFunctions()) {
                        if (!method.modifiers.contains(Modifier.SUSPEND)) {
                            logger.error("Only suspending methods are supported!", method)
                            return
                        }

                        val (clientRequestParameters: List<KSVariableParameter>, clientStreamingFlow: KSVariableParameter?) = if (method.parameters.count() == 1) {
                            val firstParam = method.parameters.first()

                            if (isFlow(firstParam.type!!)) {
                                emptyList<KSVariableParameter>() to firstParam
                            } else {
                                method.parameters to null
                            }
                        } else {
                            method.parameters to null
                        }

                        val returnType = method.returnType
                        val call: Call = when {
                            clientStreamingFlow != null && isFlow(returnType!!) -> {
                                Call.BiStream(clientStreamingFlow, returnType!!)
                            }
                            clientStreamingFlow != null && returnType != null -> {
                                Call.ClientStream(clientStreamingFlow, returnType)
                            }
                            isFlow(returnType!!) -> {
                                Call.ServerStream(clientRequestParameters, returnType!!)
                            }
                            returnType != null -> {
                                Call.SingleCall(clientRequestParameters, returnType)
                            }
                            else -> {
                                logger.error("Unknown input / output parameters!", method)
                                return
                            }
                        }

                        val clientMethodBuilder = FunSpec.builder(method.simpleName.asString())
                            .addModifiers(KModifier.SUSPEND)
                            .addModifiers(KModifier.OVERRIDE)

                        fun KSTypeReference?.asTypeName(): TypeName {
                            val resolved = this!!.resolve()!!
                            val className = ClassName(resolved.declaration.packageName.asString(), resolved.declaration.simpleName.asString())
                            if (resolved.arguments.isEmpty()) {
                                return className
                            } else {
                                return ParameterizedTypeName.get(
                                    className,
                                    *resolved.arguments.map { it.type.asTypeName() }.toTypedArray()
                                )
                            }
                        }

                        when (call) {
                            is Call.SingleCall -> {
                                clientMethodBuilder.addParameters(
                                    call.requestType.map {
                                        ParameterSpec
                                            .builder(it.name!!.getShortName(), it.type.asTypeName())
                                            .build()
                                    }
                                )
                                clientMethodBuilder.returns(
                                    call.responseType.asTypeName()
                                )
                                clientMethodBuilder.addCode("""
                                    serviceClient.singleCall(
                                        ${call.requestType.map { it.name!!.getShortName() }.joinToString(" ,")}
                                    )
                                """.trimIndent())
                            }
                            is Call.ClientStream -> {
                                clientMethodBuilder.addParameter(
                                    ParameterSpec
                                        .builder(call.upstreamFlow.name!!.getShortName(), call.upstreamFlow.type.asTypeName())
                                        .build()
                                )
                                clientMethodBuilder.returns(
                                    call.responseType.asTypeName()
                                )
                            }
                            is Call.ServerStream -> {
                                clientMethodBuilder.addParameters(
                                    call.requestType.map {
                                        ParameterSpec
                                            .builder(it.name!!.getShortName(), it.type.asTypeName())
                                            .build()
                                    }
                                )
                                clientMethodBuilder.returns(
                                    call.downstreamFlow.asTypeName()
                                )
                            }
                            is Call.BiStream -> {
                                clientMethodBuilder.addParameter(
                                    ParameterSpec
                                        .builder(call.upstreamFlow.name!!.getShortName(), call.upstreamFlow.type.asTypeName())
                                        .build()
                                )
                                clientMethodBuilder.returns(
                                    call.downstreamFlow.asTypeName()
                                )
                            }
                        }

                        clientBuilder.addFunction(clientMethodBuilder.build())
                        logger.info("${method.simpleName.asString()} Call: $call")
                    }

                    val client = clientBuilder.build()

                    val implBaseName = "${symbol.simpleName.asString()}ImplBase"
                    val implBase = TypeSpec.classBuilder(implBaseName)
                        .addModifiers(KModifier.ABSTRACT)
                        .addSuperinterface(ClassName(symbol.packageName.asString(), symbol.simpleName.asString()))
                        .build()

                    codeGenerator.createNewFile(symbol.packageName.asString(), clientName).bufferedWriter().use {
                        FileSpec.builder(symbol.packageName.asString(), clientName)
                            .addType(client)
                            .build()
                            .writeTo(it)
                    }

                    codeGenerator.createNewFile(symbol.packageName.asString(), implBaseName).bufferedWriter().use {
                        FileSpec.builder(symbol.packageName.asString(), implBaseName)
                            .addType(implBase)
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