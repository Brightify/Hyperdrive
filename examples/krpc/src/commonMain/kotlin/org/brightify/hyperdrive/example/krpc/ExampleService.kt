package org.brightify.hyperdrive.example.krpc

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.yield
import org.brightify.hyperdrive.krpc.api.EnableKRPC

@EnableKRPC
interface ExampleService {
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

// fun makeClient(): ExampleService {
//     Logger.setLevel(LoggingLevel.Trace)
//     val connection = LoopbackConnection(MainScope(), 5_000)
//
//     val impl = DefaultExampleService()
//     val registry = DefaultServiceRegistry()
//     registry.register(ExampleService.Descriptor.describe(impl))
//     val protocol = AscensionRPCProtocol.Factory(registry).create(connection)
//
//     return DefaultRPCHandshakePerformer.Behavior.Client(protocol)
// }