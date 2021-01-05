package org.brightify.hyperdrive.krpc

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.joinToCode
import org.brightify.hyperdrive.krpc.api.Error
import org.brightify.hyperdrive.krpc.util.asTypeName
import org.brightify.hyperdrive.krpc.util.singleTypeParameter
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter

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
        private val requestType: List<KSValueParameter>,
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
                        ParameterSpec.builder(it.name!!.getShortName(), it.type.asTypeName())
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
            return PropertySpec.builder(
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

    class ClientStream(method: KSFunctionDeclaration, val requestType: List<KSValueParameter>, val upstreamFlow: KSValueParameter, val responseType: KSTypeReference): Call(method) {
        private val request: TypeName = if (requestType.isEmpty()) {
            ClassName("kotlin", "Unit")
        } else {
            KnownType.Hyperdrive.dataWrapper(requestType.count()).parameterizedBy(requestType.map { it.type.asTypeName() })
        }

        private val response: TypeName = responseType.asTypeName()

        override fun clientMethodBuilder(descriptor: TypeName): FunSpec.Builder = super.clientMethodBuilder(descriptor)
            .addParameters(
                requestType.map {
                    ParameterSpec.builder(it.name!!.getShortName(), it.type.asTypeName())
                        .build()
                }
            )
            .addParameter(
                ParameterSpec.builder(upstreamFlow.name!!.getShortName(), upstreamFlow.type.asTypeName())
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
            val descriptorType = KnownType.Hyperdrive.coldUpstreamCallDescriptor(
                request,
                upstreamFlow.type.singleTypeParameter().type.asTypeName(),
                response
            )
            return PropertySpec.builder(
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

    class ServerStream(method: KSFunctionDeclaration, val requestType: List<KSValueParameter>, val downstreamFlow: KSTypeReference): Call(method) {
        private val request: TypeName = if (requestType.isEmpty()) {
            ClassName("kotlin", "Unit")
        } else {
            KnownType.Hyperdrive.dataWrapper(requestType.count()).parameterizedBy(requestType.map { it.type.asTypeName() })
        }

        override fun clientMethodBuilder(descriptor: TypeName): FunSpec.Builder = super.clientMethodBuilder(descriptor)
            .addParameters(
                requestType.map {
                    ParameterSpec.builder(it.name!!.getShortName(), it.type.asTypeName())
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
            val descriptorType =
                KnownType.Hyperdrive.coldDownstreamCallDescriptor(request, downstreamFlow.singleTypeParameter().type.asTypeName())
            return PropertySpec.builder(
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

    class BiStream(method: KSFunctionDeclaration, val requestType: List<KSValueParameter>, val upstreamFlow: KSValueParameter, val downstreamFlow: KSTypeReference): Call(method) {
        private val request: TypeName = if (requestType.isEmpty()) {
            ClassName("kotlin", "Unit")
        } else {
            KnownType.Hyperdrive.dataWrapper(requestType.count()).parameterizedBy(requestType.map { it.type.asTypeName() })
        }

        override fun clientMethodBuilder(descriptor: TypeName): FunSpec.Builder = super.clientMethodBuilder(descriptor)
            .addParameter(
                ParameterSpec.builder(upstreamFlow.name!!.getShortName(), upstreamFlow.type.asTypeName())
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
            val descriptorType = KnownType.Hyperdrive.coldBistreamCallDescriptor(
                request,
                upstreamFlow.type.singleTypeParameter().type.asTypeName(),
                downstreamFlow.singleTypeParameter().type.asTypeName()
            )
            return PropertySpec.builder(
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