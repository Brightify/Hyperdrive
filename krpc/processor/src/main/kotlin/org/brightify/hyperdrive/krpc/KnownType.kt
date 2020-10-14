package org.brightify.hyperdrive.krpc

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName

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