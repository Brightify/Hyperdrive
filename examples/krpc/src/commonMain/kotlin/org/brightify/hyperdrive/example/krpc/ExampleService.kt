package org.brightify.hyperdrive.example.krpc

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.api.BaseRPCError
import org.brightify.hyperdrive.krpc.api.EnableKRPC
import org.brightify.hyperdrive.krpc.api.Error
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.impl.DefaultServiceRegistry
import org.brightify.hyperdrive.krpc.impl.JsonCombinedSerializer
import org.brightify.hyperdrive.krpc.impl.JsonTransportFrameSerializer
import org.brightify.hyperdrive.krpc.impl.SerializerRegistry
import org.brightify.hyperdrive.krpc.protocol.DefaultRPCNode
import org.brightify.hyperdrive.krpc.protocol.ascension.AscensionRPCProtocol
import org.brightify.hyperdrive.krpc.protocol.ascension.RPCHandshakePerformer
import org.brightify.hyperdrive.krpc.test.LoopbackConnection
import org.brightify.hyperdrive.multiplatformx.MultiplatformGlobalScope

@Serializable
class NotFound: BaseRPCError(RPCError.StatusCode.NotFound, "Was not found")

@Serializable
class Unauthenticated: BaseRPCError(RPCError.StatusCode.Forbidden, "Unauthenticated was")

@EnableKRPC
interface ExampleService {
    @Error(NotFound::class, Unauthenticated::class)
    suspend fun strlen(parameter: String): Int

    suspend fun sum(numbers: Flow<Int>): Int

    suspend fun flowOfRange(start: Int, end: Int): Flow<Int>

    suspend fun streamingStrlen(texts: Flow<String>): Flow<Int>



    // class Client private constructor(): ExampleService {
    //     private lateinit var transport: RPCTransport
    //
    //     constructor(transport: RPCTransport): this() {
    //         this.transport = transport
    //     }
    //
    //     override suspend fun strlen(parameter: String): Int {
    //         return this.transport.singleCall<RPCDataWrapper1<String>, Int>(serviceCall = Descriptor.Call.strlen, RPCDataWrapper1(parameter))
    //     }
    //
    // }
    //
    // object Descriptor: ServiceDescriptor<ExampleService> {
    //     object Call {
    //         val strlen: ClientCallDescriptor<RPCDataWrapper1<String>, Int>
    //             get(): ClientCallDescriptor<RPCDataWrapper1<String>, Int> {
    //                 return ClientCallDescriptor<RPCDataWrapper1<String>, Int>(identifier = ServiceCallIdentifier(serviceId = serviceIdentifier, callId = "strlen"), outgoingSerializer = serializer<RPCDataWrapper1<String>>(), incomingSerializer = serializer<Int>(), errorSerializer = RPCErrorSerializer())
    //             }
    //
    //     }
    //
    //     override fun describe(service: ExampleService): ServiceDescription {
    //         return ServiceDescription(
    //             identifier = serviceIdentifier,
    //             calls = listOf<CallDescriptor<*>>(
    //                 Call.strlen.calling { request ->
    //                     return@calling service.strlen(parameter = request.component1())
    //                 }
    //             )
    //         )
    //     }
    //
    //     val serviceIdentifier: String
    //         get(): String {
    //             return "ExampleService"
    //         }
    //
    // }
}

class DefaultExampleService: ExampleService {
    override suspend fun strlen(parameter: String): Int {
        return parameter.length
    }

    override suspend fun sum(numbers: Flow<Int>): Int {
        return numbers.fold(0) { acc, value -> acc + value }
    }

    override suspend fun flowOfRange(start: Int, end: Int): Flow<Int> {
        return flow {
            for (index in start until end) {
                emit(index)
                yield()
            }
        }
    }

    override suspend fun streamingStrlen(texts: Flow<String>): Flow<Int> {
        return texts.map {
            it.length
        }
    }
}

suspend fun makeClient(): ExampleService = withContext(MultiplatformGlobalScope.coroutineContext) {
    val logger = Logger<ExampleService>()
    val connection = LoopbackConnection(this, 5_000)

    val impl = DefaultExampleService()
    val serializers = SerializerRegistry(
        JsonCombinedSerializer.Factory()
    )
    val registry = DefaultServiceRegistry()
    registry.register(ExampleService.Descriptor.describe(impl))
    val nodeFactory = DefaultRPCNode.Factory(
        object: RPCHandshakePerformer {
            override suspend fun performHandshake(connection: RPCConnection): RPCHandshakePerformer.HandshakeResult {
                return RPCHandshakePerformer.HandshakeResult.Success(
                    JsonTransportFrameSerializer(),
                    AscensionRPCProtocol.Factory()
                )
            }
        },
        serializers.payloadSerializerFactory,
        emptyList(),
        registry,
    )
    val node = nodeFactory.create(connection)
    launch { node.run { } }
    logger.trace { "Did we even get here?" }
    val client = ExampleService.Client(node.transport) as ExampleService
    println(client.strlen("Yo!"))
    client
}

fun runClient(result: (Int?, Throwable?) -> Unit) {
    MultiplatformGlobalScope.launch {
        try {
            val client = makeClient()
            result(client.strlen("yo"), null)
        } catch (t: Throwable) {
            result(null, t)
        }
    }
}