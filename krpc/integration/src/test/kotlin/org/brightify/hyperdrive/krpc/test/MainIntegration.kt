package org.brightify.hyperdrive.krpc.test

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.serializer
import org.brightify.hyperdrive.client.impl.ProtoBufWebSocketFrameConverter
import org.brightify.hyperdrive.client.impl.ServiceClientImpl
import org.brightify.hyperdrive.client.impl.SingleFrameConverterWrapper
import org.brightify.hyperdrive.client.impl.WebSocketClient
import org.brightify.hyperdrive.krpc.api.*
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer
import org.brightify.hyperdrive.krpc.server.impl.DefaultServiceRegistry
import org.brightify.hyperdrive.krpc.server.impl.KtorServerFrontend
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals

class MainIntegration {

    private lateinit var serverFrontend: KtorServerFrontend
    private lateinit var client: ServiceClientImpl

    @BeforeEach
    fun setup() {
//        val mockResolver = object: RPCSerializerResolver {
//            override fun serializerFor(header: RPCFrame.Header<*>): KSerializer<Any?>? {
//                println(header)
//                return Int.serializer() as KSerializer<Any?>
//            }
//        }

        serverFrontend = KtorServerFrontend(
            frameConverter = SingleFrameConverterWrapper.binary(
                ProtoBufWebSocketFrameConverter(
                    outgoingSerializer = RPCFrameSerializationStrategy(),
                    incomingDeserializer = RPCFrameDeserializationStrategy()
                )
            ),
            serviceRegistry = DefaultServiceRegistry(),
            responseScope = GlobalScope
        )

        val clientTransport = WebSocketClient(
            connectionScope = GlobalScope,
            frameConverter = SingleFrameConverterWrapper.binary(
                ProtoBufWebSocketFrameConverter(
                    outgoingSerializer = RPCFrameSerializationStrategy(),
                    incomingDeserializer = RPCFrameDeserializationStrategy()
                )
            )
        )
        client = ServiceClientImpl(clientTransport, GlobalScope, GlobalScope)
    }

    @AfterEach
    fun teardown() {
        serverFrontend.shutdown()
        client.shutdown()
    }

    @Test
    fun `perform single call`() = runBlocking {
        serverFrontend.register(ServiceDescription("MainIntegrationTest", listOf(
            CallDescriptor.Single(ServiceCallIdentifier("MainIntegrationTest", "perform single call"), serializer<Int>(), serializer<Int>(), RPCErrorSerializer()) {
                (it) / 2
            }
        )))

        try {
            val response = client.singleCall<Int, Int>(ClientCallDescriptor(ServiceCallIdentifier("MainIntegrationTest", "perform single call"), serializer(), serializer(), RPCErrorSerializer()), 5)
            assertEquals(2, response)
        } catch (t: Throwable) {
            t.printStackTrace()
            throw t
        }
    }

    @Test
    fun `perform upstream call`() = runBlocking {
        serverFrontend.register(ServiceDescription("MainIntegrationTest", listOf(
            CallDescriptor.ColdUpstream(ServiceCallIdentifier("MainIntegrationTest", "perform outstream call"), serializer<Unit>(), serializer<Int>(), serializer<Int>(), RPCErrorSerializer()) { _, stream ->
                stream.take(5).reduce { a, b ->
                    println("a: $a, b: $b")
                    a + b
                }
            }
        )))

        try {
            val response = client.clientStream<Unit, Int, Int>(
                ColdUpstreamCallDescriptor(ServiceCallIdentifier("MainIntegrationTest", "perform outstream call"), serializer(), serializer(), serializer(), RPCErrorSerializer()),
                Unit,
                flow {
                    var i = 1
                    while (isActive) {
                        emit(i++)
                        yield()
                    }
                }.take(10)
            )
            assertEquals(15, response)
        } catch (t: Throwable) {
            t.printStackTrace()
            throw t
        }
    }
}