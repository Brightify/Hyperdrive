package org.brightify.hyperdrive.example.krpc

import kotlinx.serialization.serializer
import org.brightify.hyperdrive.krpc.api.CallDescriptor
import org.brightify.hyperdrive.krpc.api.ClientCallDescriptor
import org.brightify.hyperdrive.krpc.api.EnableKRPC
import org.brightify.hyperdrive.krpc.api.RPCDataWrapper1
import org.brightify.hyperdrive.krpc.api.RPCTransport
import org.brightify.hyperdrive.krpc.api.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.api.ServiceDescription
import org.brightify.hyperdrive.krpc.api.ServiceDescriptor
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer

@EnableKRPC
interface ExampleService {
    suspend fun strlen(parameter: String): Int

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

// class DefaultExampleService: ExampleService {
//     override suspend fun strlen(parameter: String): Int {
//         return parameter.length
//     }
// }

// fun test() {
//     // val client = ExampleService.Client(null as RPCTransport)
//     // val descriptor = ExampleService.Descriptor
//     // val call = ExampleService.Descriptor.Call.strlen
// }