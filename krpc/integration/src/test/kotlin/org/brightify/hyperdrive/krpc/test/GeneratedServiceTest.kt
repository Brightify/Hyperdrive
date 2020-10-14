package org.brightify.hyperdrive.krpc.test

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.take
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.serializer
import org.brightify.hyperdrive.client.impl.ProtoBufWebSocketFrameConverter
import org.brightify.hyperdrive.client.impl.ServiceClientImpl
import org.brightify.hyperdrive.client.impl.SingleFrameConverterWrapper
import org.brightify.hyperdrive.client.impl.WebSocketClient
import org.brightify.hyperdrive.krpc.api.CallDescriptor
import org.brightify.hyperdrive.krpc.api.ClientCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdUpstreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.RPCError
import org.brightify.hyperdrive.krpc.api.RPCFrameDeserializationStrategy
import org.brightify.hyperdrive.krpc.api.RPCFrameSerializationStrategy
import org.brightify.hyperdrive.krpc.api.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.api.ServiceDescription
import org.brightify.hyperdrive.krpc.server.impl.DefaultServiceRegistry
import org.brightify.hyperdrive.krpc.server.impl.KtorServer
import org.brightify.hyperdrive.krpc.test.BasicTestServiceClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalStateException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class GeneratedServiceTest {

    private val serviceImpl = object: BasicTestService {
        override suspend fun multiplyByTwo(source: Int): Int {
            return source * 2
        }

        override suspend fun multiply(source: Int, multiplier: Int): Int {
            return source * multiplier
        }

        override suspend fun singleCallError() {
            throw IllegalArgumentError("source cannot be zero")
        }

        override suspend fun sum(stream: Flow<Int>): Int {
            return stream.reduce { accumulator, value -> accumulator + value }
        }

        override suspend fun sumWithInitial(initialValue: Int, stream: Flow<Int>): Int {
            return initialValue + stream.reduce { accumulator, value -> accumulator + value }
        }

        override suspend fun clientStreamError(stream: Flow<Unit>): IllegalArgumentError {
            try {
                stream.collect()
                error("Expected exception not thrown!")
            } catch (e: IllegalArgumentError) {
                return e
            }
        }

        override suspend fun timer(count: Int): Flow<Int> {
            return ticker(1, 0).receiveAsFlow().withIndex().map { it.index }.take(count)
        }

        override suspend fun multiplyEachByTwo(stream: Flow<Int>): Flow<Int> {
            return stream.map { it * 2 }
        }
    }


    private lateinit var server: KtorServer
    private lateinit var client: ServiceClientImpl

    @BeforeEach
    fun setup() {
        println("New test ------------------------")
        server = KtorServer(
            frameConverter = SingleFrameConverterWrapper.binary(
                ProtoBufWebSocketFrameConverter(
                    outgoingSerializer = RPCFrameSerializationStrategy(),
                    incomingDeserializer = RPCFrameDeserializationStrategy()
                )
            ),
            serviceRegistry = DefaultServiceRegistry(),
            responseScope = GlobalScope
        )
        server.register(BasicTestServiceDescriptor.describe(serviceImpl))

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
    fun tearDown() {
        client.shutdown()
        server.shutdown()
    }

    @Test
    fun `test generated simple call`(): Unit = runBlocking {
        val service = BasicTestServiceClient(client)

        assertEquals(2, service.multiplyByTwo(1))
        assertEquals(4, service.multiply(2, 2))

        assertFailsWith(IllegalArgumentError::class) { service.singleCallError() }
    }

    @Test
    fun `perform generated upstream call`(): Unit = runBlocking {
        val service = BasicTestServiceClient(client)

        assertEquals(21, service.sum(flowOf(1, 2, 3, 4, 5, 6)))
        assertEquals(30, service.sumWithInitial(9, flowOf(1, 2, 3, 4, 5, 6)))

        assertFailsWith(IllegalArgumentError::class) {
            throw service.clientStreamError(flow { throw IllegalArgumentError("Expected error") })
        }
    }

    @Test
    fun `perform generated downstream call`() = runBlocking {
        val service = BasicTestServiceClient(client)

        assertEquals(15, service.timer(6).reduce { accumulator, value -> accumulator + value })
    }

    @Test
    fun `perform generated bistream call`() = runBlocking {
        val service = BasicTestServiceClient(client)

        assertEquals(42, service.multiplyEachByTwo(flowOf(1, 2, 3, 4, 5, 6)).reduce { accumulator, value -> accumulator + value })
    }
}