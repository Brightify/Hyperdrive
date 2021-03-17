package org.brightify.hyperdrive.krpc.impl

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.first
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.serialization.builtins.serializer
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.LoggingLevel
import org.brightify.hyperdrive.krpc.api.CallDescriptor
import org.brightify.hyperdrive.krpc.api.ClientCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdBistreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdDownstreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.ColdUpstreamCallDescriptor
import org.brightify.hyperdrive.krpc.api.RPCConnection
import org.brightify.hyperdrive.krpc.api.RPCTransport
import org.brightify.hyperdrive.krpc.api.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.api.ServiceDescription
import org.brightify.hyperdrive.krpc.api.ServiceDescriptor
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer
import org.brightify.hyperdrive.krpc.api.impl.AscensionRPCProtocol
import org.brightify.hyperdrive.krpc.api.impl.DefaultServiceRegistry
import org.brightify.hyperdrive.krpc.api.impl.ServiceRegistry
import org.brightify.hyperdrive.krpc.test.LoopbackConnection
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

@OptIn(ExperimentalCoroutinesApi::class)
internal class AscentionProtocolTest {
    private val testScope = TestCoroutineScope()
    private lateinit var registry: ServiceRegistry
    private lateinit var client: TestService

    companion object {
        @BeforeAll
        @JvmStatic
        fun preSetup() {
            Logger.setLevel(LoggingLevel.Trace)
        }
    }

    @BeforeEach
    fun setup() {
        val connection = LoopbackConnection(testScope)
        registry = DefaultServiceRegistry()

        val protocol = AscensionRPCProtocol.Factory(registry, testScope, testScope).create(connection)
        client = TestService.Client(protocol)
    }

    @AfterEach
    fun teardown() {
        testScope.cleanupTestCoroutines()
    }

    @Test
    fun `SingleCall - Succeeds`() = testScope.runBlockingTest {
        register(
            object: BaseTestService() {
                override suspend fun singleCall(): String {
                    return "Hello world!"
                }
            }
        )


        try {
            println(client.singleCall())
        } catch (t: Throwable) {
            println("WTF: $t")
        }
        assertEquals("Hello world!", client.singleCall())
        assertEquals("Hello world!", client.singleCall())
    }

    @Test
    fun `ClientStream - Waits for the end of client flow`() = testScope.runBlockingTest {
        register(
            object: BaseTestService() {
                override suspend fun clientStream(flow: Flow<Int>): String {
                    return "Hello " + flow.fold(0) { acc, value -> acc + value }.toString()
                }
            }
        )

        assertEquals("Hello 15", client.clientStream(flowOf(1, 2, 3, 4, 5)))
        assertEquals("Hello 0", client.clientStream(flowOf()))
        assertEquals("Hello 3", client.clientStream(flowOf(1, 2, 3, 4, 5).take(2)))
    }

    @Test
    fun `ClientStream - Not collecting client stream`() = testScope.runBlockingTest {
        register(
            object: BaseTestService() {
                override suspend fun clientStream(flow: Flow<Int>): String {
                    return "Hello World!"
                }
            }
        )

        assertEquals("Hello World!", client.clientStream(flowOf(1, 2, 3, 4, 5).onStart { error("Should not start") }))
        assertEquals("Hello World!", client.clientStream(emptyFlow<Int>().onStart { error("Should not start") }))
        assertEquals("Hello World!", client.clientStream(flowOf(1, 2, 3, 4, 5).take(2).onStart { error("Should not start") }))
    }

    @Test
    fun `ClientStream - Return before client stream completes`() = testScope.runBlockingTest {
        val result = Channel<Int>()

        register(
            object: BaseTestService() {
                override suspend fun clientStream(flow: Flow<Int>): String {
                    val wait = CompletableDeferred<Unit>()
                    coroutineScope {
                        this.launch {
                            val first = flow.first()
                            wait.complete(Unit)
                            delay(1_000)
                            result.send(flow.fold(first) { acc, value -> acc + value })
                        }
                    }
                    return "Hello World!"
                }
            }
        )

        val resultFlow = result.consumeAsFlow()

        assertEquals("Hello World!", client.clientStream(flowOf(1, 2, 3, 4, 5)).also { println("R1: $it") })
        assertEquals(15, resultFlow.first().also { println("R2: $it") })

        // assertEquals("Hello World!", client.clientStream(flowOf()))
        // assertEquals(0, resultFlow.first())
        //
        // assertEquals("Hello World!", client.clientStream(flowOf(1, 2, 3, 4, 5).take(2)))
        // assertEquals(3, resultFlow.first())
    }

    @Test
    fun `ServerStream - Sends all flow events`() = testScope.runBlockingTest {
        register(
            object: BaseTestService() {
                override suspend fun serverStream(request: Int): Flow<String> {
                    return if (request < 1) {
                        emptyFlow()
                    } else {
                        (1..request).asFlow().map { "$it" }
                    }
                }
            }
        )

        assertEquals(listOf("1", "2", "3", "4", "5"), client.serverStream(5).toList())
        assertEquals(emptyList<String>(), client.serverStream(0).toList())
        assertEquals(listOf("1", "2"), client.serverStream(2).take(2).toList())
    }

    @Test
    fun `Bistream - Sends and receives all flow events`() = testScope.runBlockingTest {
        register(
            object: BaseTestService() {
                override suspend fun bidiStream(flow: Flow<Int>): Flow<String> {
                    return flow.map { it.toString() }
                }
            }
        )

        assertEquals(listOf("1", "2", "3", "4", "5"), client.bidiStream(flowOf(1, 2, 3, 4, 5)).toList())
        assertEquals(emptyList<String>(), client.bidiStream(emptyFlow()).toList())
        assertEquals(listOf("1", "2"), client.bidiStream(flowOf(1, 2, 3, 4, 5).take(2)).toList())
    }

    private fun register(testService: TestService) {
        registry.register(
            TestService.Descriptor.describe(
                testService
            )
        )
    }

    interface TestService {
        suspend fun singleCall(): String

        suspend fun clientStream(flow: Flow<Int>): String

        suspend fun serverStream(request: Int): Flow<String>

        suspend fun bidiStream(flow: Flow<Int>): Flow<String>

        class Client(private val transport: RPCTransport): TestService {
            override suspend fun singleCall(): String {
                return transport.singleCall(Descriptor.Call.singleCall, Unit)
            }

            override suspend fun clientStream(flow: Flow<Int>): String {
                return transport.clientStream(Descriptor.Call.clientStream, Unit, flow)
            }

            override suspend fun serverStream(request: Int): Flow<String> {
                return transport.serverStream(Descriptor.Call.serverStream, request)
            }

            override suspend fun bidiStream(flow: Flow<Int>): Flow<String> {
                return transport.biStream(Descriptor.Call.bidiStream, Unit, flow)
            }
        }

        object Descriptor: ServiceDescriptor<TestService> {
            val serviceIdentifier = "TestService"

            override fun describe(service: TestService): ServiceDescription {
                return ServiceDescription(
                    serviceIdentifier,
                    listOf(
                        Call.singleCall.calling { service.singleCall() },
                        Call.clientStream.calling { _, clientStream -> service.clientStream(clientStream) },
                        Call.serverStream.calling { service.serverStream(it) },
                        Call.bidiStream.calling { _, clientStream -> service.bidiStream(clientStream) },
                    )
                )
            }

            object Call {
                val singleCall = ClientCallDescriptor(
                    ServiceCallIdentifier(serviceIdentifier, "singleCall"),
                    Unit.serializer(),
                    String.serializer(),
                    RPCErrorSerializer(),
                )

                val clientStream = ColdUpstreamCallDescriptor(
                    ServiceCallIdentifier(serviceIdentifier, "clientStream"),
                    Unit.serializer(),
                    Int.serializer(),
                    String.serializer(),
                    RPCErrorSerializer(),
                )

                val serverStream = ColdDownstreamCallDescriptor(
                    ServiceCallIdentifier(serviceIdentifier, "serverStream"),
                    Int.serializer(),
                    String.serializer(),
                    RPCErrorSerializer(),
                )

                val bidiStream = ColdBistreamCallDescriptor(
                    ServiceCallIdentifier(serviceIdentifier, "bidiStream"),
                    Unit.serializer(),
                    Int.serializer(),
                    String.serializer(),
                    RPCErrorSerializer(),
                )
            }
        }
    }

    abstract class BaseTestService: TestService {
        override suspend fun singleCall(): String {
            error("Not implemented!")
        }

        override suspend fun clientStream(flow: Flow<Int>): String {
            error("Not implemented!")
        }

        override suspend fun serverStream(request: Int): Flow<String> {
            error("Not implemented!")
        }

        override suspend fun bidiStream(flow: Flow<Int>): Flow<String> {
            error("Not implemented!")
        }
    }
}