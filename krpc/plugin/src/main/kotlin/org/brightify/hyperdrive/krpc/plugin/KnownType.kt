package org.brightify.hyperdrive.krpc.plugin

import kotlinx.coroutines.flow.Flow
import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.api.EnableKRPC
import org.brightify.hyperdrive.krpc.api.Error
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.description.ColdBistreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdDownstreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdUpstreamCallDescription
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.description.ServiceDescription
import org.brightify.hyperdrive.krpc.description.ServiceDescriptor
import org.brightify.hyperdrive.krpc.description.SingleCallDescription
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer
import org.brightify.hyperdrive.krpc.util.RPCDataWrapper1
import org.brightify.hyperdrive.krpc.util.RPCDataWrapper2
import org.brightify.hyperdrive.krpc.util.RPCDataWrapper3
import org.brightify.hyperdrive.krpc.util.RPCDataWrapper4
import org.brightify.hyperdrive.krpc.util.RPCDataWrapper5
import org.brightify.hyperdrive.krpc.util.RPCDataWrapper6
import org.brightify.hyperdrive.krpc.util.RPCDataWrapper7
import org.brightify.hyperdrive.krpc.util.RPCDataWrapper8
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.reflect.KClass

object KnownType {
    object Kotlin {
        val listOf = FqName("kotlin.collections.listOf")
        val list = fqName<List<*>>()
    }

    object Coroutines {
        val flow = fqName<Flow<*>>()
    }

    object Serialization {
        val serializer = FqName("kotlinx.serialization.serializer")
        val polymorphicModuleBuilder = FqName("kotlinx.serialization.modules.PolymorphicModuleBuilder")
        val kserializer = FqName("kotlinx.serialization.KSerializer")
    }

    object Annotation {
        val enableKrpc = fqName<EnableKRPC>()
        val error = fqName<Error>()
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
        val transport = fqName<RPCTransport>()
        val transportClassId = classIdOf(RPCTransport::class)
        val serviceDescriptor = classIdOf(ServiceDescriptor::class)
        val serviceDescription= classIdOf(ServiceDescription::class)
        val serviceCallIdentifier = fqName<ServiceCallIdentifier>()
        val rpcErrorSerializer = fqName<RPCErrorSerializer>()
        val rpcError = fqName<RPCError>()
        val runnableCallDescription = fqName<RunnableCallDescription<*>>()

        val singleCallDescription = fqName<SingleCallDescription<*, *>>()
        val coldUpstreamCallDescription = fqName<ColdUpstreamCallDescription<*, *, *>>()
        val coldDownstreamCallDescription = fqName<ColdDownstreamCallDescription<*, *>>()
        val coldBistreamCallDescription = fqName<ColdBistreamCallDescription<*, *, *>>()

        fun requestWrapper(parameterCount: Int): FqName = when (parameterCount) {
            0 -> fqName<Unit>()
            1 -> fqName<RPCDataWrapper1<*>>()
            2 -> fqName<RPCDataWrapper2<*, *>>()
            3 -> fqName<RPCDataWrapper3<*, *, *>>()
            4 -> fqName<RPCDataWrapper4<*, *, *, *>>()
            5 -> fqName<RPCDataWrapper5<*, *, *, *, *>>()
            6 -> fqName<RPCDataWrapper6<*, *, *, *, *, *>>()
            7 -> fqName<RPCDataWrapper7<*, *, *, *, *, *, *>>()
            8 -> fqName<RPCDataWrapper8<*, *, *, *, *, *, *, *>>()
            else -> error("Parameter count $parameterCount not supported. Only up to 8 parameters are supported.")
        }

        private fun <T: Any> classIdOf(cls: KClass<T>): ClassId
            = ClassId(FqName(cls.java.`package`.name), Name.identifier(cls.java.simpleName))
    }

    private inline fun <reified T> fqName(): FqName {
        return FqName(T::class.qualifiedName!!)
    }
}