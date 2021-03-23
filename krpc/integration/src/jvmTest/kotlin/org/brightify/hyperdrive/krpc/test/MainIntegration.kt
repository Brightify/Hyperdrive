package org.brightify.hyperdrive.krpc.test

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.serializer
import org.brightify.hyperdrive.krpc.client.impl.ServiceClient
import org.brightify.hyperdrive.krpc.api.*
import org.brightify.hyperdrive.krpc.client.impl.ktor.ProtoBufWebSocketFrameConverter
import org.brightify.hyperdrive.krpc.client.impl.ktor.SingleFrameConverterWrapper
import org.brightify.hyperdrive.krpc.client.impl.ktor.WebSocketClient
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.SingleCallDescription
import org.brightify.hyperdrive.krpc.description.ColdUpstreamCallDescription
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.description.ServiceDescription
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer
import org.brightify.hyperdrive.krpc.frame.serialization.RPCFrameDeserializationStrategy
import org.brightify.hyperdrive.krpc.frame.serialization.RPCFrameSerializationStrategy
import org.brightify.hyperdrive.krpc.impl.DefaultServiceRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals

class MainIntegration {

    private lateinit var serverFrontend: org.brightify.hyperdrive.krpc.server.impl.ktor.KtorServerFrontend
    private lateinit var client: ServiceClient

    @BeforeEach
    fun setup() {
//        val mockResolver = object: RPCSerializerResolver {
//            override fun serializerFor(header: RPCFrame.Header<*>): KSerializer<Any?>? {
//                println(header)
//                return Int.serializer() as KSerializer<Any?>
//            }
//        }

        serverFrontend = org.brightify.hyperdrive.krpc.server.impl.ktor.KtorServerFrontend(
            frameConverter = SingleFrameConverterWrapper.binary(
                ProtoBufWebSocketFrameConverter(
                    outgoingSerializer = RPCFrameSerializationStrategy(),
                    incomingDeserializer = RPCFrameDeserializationStrategy()
                )
            ),
            serviceRegistry = DefaultServiceRegistry()
        )

        val clientTransport = WebSocketClient(
            frameConverter = SingleFrameConverterWrapper.binary(
                ProtoBufWebSocketFrameConverter(
                    outgoingSerializer = RPCFrameSerializationStrategy(),
                    incomingDeserializer = RPCFrameDeserializationStrategy()
                )
            )
        )
        client = ServiceClient(clientTransport, DefaultServiceRegistry(), GlobalScope)
    }

    @AfterEach
    fun teardown() {
        serverFrontend.shutdown()
        runBlocking { client.close() }
    }

    @Test
    fun `perform single call`() = runBlocking {
        serverFrontend.register(ServiceDescription("MainIntegrationTest", listOf(
            RunnableCallDescription.Single(ServiceCallIdentifier("MainIntegrationTest", "perform single call"), serializer<Int>(), serializer<Int>(), RPCErrorSerializer()) {
                (it) / 2
            }
        )))

        try {
            val response = client.singleCall<Int, Int>(SingleCallDescription(ServiceCallIdentifier("MainIntegrationTest", "perform single call"), serializer(), serializer(), RPCErrorSerializer()), 5)
            assertEquals(2, response)
        } catch (t: Throwable) {
            t.printStackTrace()
            throw t
        }
    }

    @Test
    fun `perform upstream call`() = runBlocking {
        serverFrontend.register(ServiceDescription("MainIntegrationTest", listOf(
            RunnableCallDescription.ColdUpstream(ServiceCallIdentifier("MainIntegrationTest", "perform outstream call"), serializer<Unit>(), serializer<Int>(), serializer<Int>(), RPCErrorSerializer()) { _, stream ->
                stream.take(5).reduce { a, b ->
                    println("a: $a, b: $b")
                    a + b
                }
            }
        )))

        try {
            val response = client.clientStream<Unit, Int, Int>(
                ColdUpstreamCallDescription(ServiceCallIdentifier("MainIntegrationTest", "perform outstream call"), serializer(), serializer(), serializer(), RPCErrorSerializer()),
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