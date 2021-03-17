package org.brightify.hyperdrive.krpc.plugin

import kotlinx.coroutines.flow.Flow
import org.brightify.hyperdrive.krpc.api.RPCTransport
import org.brightify.hyperdrive.krpc.api.ClientCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdUpstreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdDownstreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdBistreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.EnableKRPC
import org.brightify.hyperdrive.krpc.api.Error
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer
import org.brightify.hyperdrive.krpc.api.ServiceDescription
import org.brightify.hyperdrive.krpc.api.ServiceDescriptor
import org.brightify.hyperdrive.krpc.api.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.api.CallDescriptor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.reflect.KClass

object KnownType {
    object Kotlin {
        val listOf = FqName("kotlin.collections.listOf")
        val flow = FqName(Flow::class.qualifiedName!!)
    }

    object Coroutines {
        val flow = FqName(Flow::class.qualifiedName!!)
    }

    object Serialization {
        val serializer = FqName("kotlinx.serialization.serializer")
        val polymorphicModuleBuilder = FqName("kotlinx.serialization.modules.PolymorphicModuleBuilder")
    }

    object Annotation {
        val enableKrpc = FqName(EnableKRPC::class.qualifiedName!!)
        val error = FqName(Error::class.qualifiedName!!)
    }

    object Nested {
        val client = Name.identifier("Client")
        val descriptor = Name.identifier("Descriptor")
        val call = Name.identifier("Call")

        object Descriptor {
            val serviceIdentifier = Name.identifier("serviceIdentifier")
            val describe = Name.identifier("describe")
        }
    }

    object API {
        val transport: FqName = FqName(RPCTransport::class.qualifiedName!!)
        val transportClassId: ClassId = classIdOf(RPCTransport::class)
        val serviceDescriptor: ClassId = classIdOf(ServiceDescriptor::class)
        val serviceDescription: ClassId= classIdOf(ServiceDescription::class)
        val serviceCallIdentifier: FqName = FqName(ServiceCallIdentifier::class.qualifiedName!!)
        val rpcErrorSerializer: FqName = FqName(RPCErrorSerializer::class.qualifiedName!!)
        val rpcError: FqName = FqName(RPCError::class.qualifiedName!!)
        val callDescriptor: FqName = FqName(CallDescriptor::class.qualifiedName!!)

        val clientCallDescriptor: FqName = FqName(ClientCallDescriptor::class.qualifiedName!!)
        val clientCallDescriptorClassId: ClassId = classIdOf(ClientCallDescriptor::class)

        val coldUpstreamCallDescriptor: FqName = FqName(ColdUpstreamCallDescriptor::class.qualifiedName!!)
        val coldDownstreamCallDescriptor: FqName = FqName(ColdDownstreamCallDescriptor::class.qualifiedName!!)
        val coldBistreamCallDescriptor: FqName = FqName(ColdBistreamCallDescriptor::class.qualifiedName!!)

        fun requestWrapper(parameterCount: Int): FqName = when (parameterCount) {
            0 -> FqName("kotlin.Unit")
            else -> FqName("org.brightify.hyperdrive.krpc.api.RPCDataWrapper$parameterCount")
        }

        private fun <T: Any> classIdOf(cls: KClass<T>): ClassId
            = ClassId(FqName(cls.java.`package`.name), Name.identifier(cls.java.simpleName))
    }
}