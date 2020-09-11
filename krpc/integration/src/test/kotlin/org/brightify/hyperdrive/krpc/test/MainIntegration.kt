package org.brightify.hyperdrive.krpc.test

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import org.brightify.hyperdrive.client.api.ServiceClient
import org.brightify.hyperdrive.client.impl.ProtoBufWebSocketFrameConverter
import org.brightify.hyperdrive.client.impl.ServiceClientImpl
import org.brightify.hyperdrive.client.impl.SingleFrameConverterWrapper
import org.brightify.hyperdrive.client.impl.WebSocketClient
import org.brightify.hyperdrive.krpc.api.*
import org.brightify.hyperdrive.krpc.server.impl.KtorServer
import org.brightify.hyperdrive.krpc.server.impl.PingServiceImpl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MainIntegration {

    private lateinit var server: KtorServer
    private lateinit var client: ServiceClientImpl

    @BeforeEach
    fun setup() {
        val mockResolver = object: RPCSerializerResolver {
            override fun serializerFor(header: RPCFrame.Header<*>): KSerializer<Any?>? {
                println(header)
                return Int.serializer() as KSerializer<Any?>
            }
        }

        val serverConverter = ProtoBufWebSocketFrameConverter(
            upstreamFrameSerializer = RPCFrameSerializer(mockResolver),
            downstreamFrameSerializer = RPCFrameSerializer(mockResolver)
        )
        server = KtorServer(
            frameConverter = SingleFrameConverterWrapper.binary(serverConverter)
        )


        val clientConverter = ProtoBufWebSocketFrameConverter(
            upstreamFrameSerializer = RPCFrameSerializer(mockResolver),
            downstreamFrameSerializer = RPCFrameSerializer(mockResolver)
        )
        val clientTransport = WebSocketClient(connectionScope = GlobalScope, frameConverter = SingleFrameConverterWrapper.binary(clientConverter))
        client = ServiceClientImpl(clientTransport, GlobalScope)
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
        client.shutdown()
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `perform single call`() = runBlocking {
        server.register(ServiceDescription("MainIntegrationTest", listOf(
            CallDescriptor("perform single call", typeOf<Int>(), typeOf<Int>()) {
                (it as Int) / 2
            }
        )))


        try {
            val response = client.singleCall<Int, Int>(ServiceCallIdentifier("MainIntegrationTest", "perform single call"), 5)
            assertEquals(2, response)
        } catch (t: Throwable) {
            t.printStackTrace()
            throw t
        }
    }

}