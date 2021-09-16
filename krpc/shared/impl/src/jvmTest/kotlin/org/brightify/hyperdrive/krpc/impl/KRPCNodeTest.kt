package org.brightify.hyperdrive.krpc.impl

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineExceptionHandler
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.serialization.builtins.serializer
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.LoggingLevel
import org.brightify.hyperdrive.krpc.MutableServiceRegistry
import org.brightify.hyperdrive.krpc.description.SingleCallDescription
import org.brightify.hyperdrive.krpc.description.ColdBistreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdDownstreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdUpstreamCallDescription
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.SerializationFormat
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.description.ServiceDescription
import org.brightify.hyperdrive.krpc.description.ServiceDescriptor
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer
import org.brightify.hyperdrive.krpc.protocol.ascension.AscensionRPCProtocol
import org.brightify.hyperdrive.krpc.protocol.DefaultRPCNode
import org.brightify.hyperdrive.krpc.protocol.ascension.PayloadSerializer
import org.brightify.hyperdrive.krpc.protocol.ascension.RPCHandshakePerformer
import org.brightify.hyperdrive.krpc.test.LoopbackConnection

@OptIn(ExperimentalCoroutinesApi::class)
class KRPCNodeTest: BehaviorSpec({
    val testScope = TestCoroutineScope(TestCoroutineExceptionHandler())

    beforeSpec {
        Logger.configure { setMinLevel(LoggingLevel.Trace) }
    }

    afterTest {
        testScope.cleanupTestCoroutines()
    }

    Given("KRPCNode") {
        lateinit var registry: MutableServiceRegistry
        lateinit var connection: RPCConnection
        lateinit var client: TestService

        fun register(testService: TestService) {
            registry.register(
                TestService.Descriptor.describe(
                    testService
                )
            )
        }

        beforeTest {
            connection = LoopbackConnection(testScope)
            registry = DefaultServiceRegistry()
            val serializers = SerializerRegistry(
                JsonCombinedSerializer.Factory(),
            )
            val node = DefaultRPCNode.Factory(
                RPCHandshakePerformer.NoHandshake(
                    JsonTransportFrameSerializer(),
                    AscensionRPCProtocol.Factory(),
                ),
                serializers.payloadSerializerFactory,
                listOf(),
                registry,
            ).create(connection)
            testScope.launch { node.run { } }
            val transport = node.transport
            client = TestService.Client(transport)
        }

        afterTest {
            connection.close()
        }

        And("A single call") {
            beforeTest {
                register(object: BaseTestService() {
                    override suspend fun singleCall(): String {
                        return "Hello world!"
                    }
                })
            }

            When("Calling it once") {
                Then("Returns a value") {
                    client.singleCall() shouldBe "Hello world!"
                }
            }

            When("Calling it multiple times") {
                Then("Returns the same value") {
                    client.singleCall() shouldBe "Hello world!"
                    client.singleCall() shouldBe "Hello world!"
                    client.singleCall() shouldBe "Hello world!"
                }
            }
        }

        And("A client stream call collecting the flow") {
            beforeTest {
                register(object: BaseTestService() {
                    override suspend fun clientStream(flow: Flow<Int>): String {
                        return "Hello " + flow.fold(0) { acc, value -> acc + value }.toString()
                    }
                })
            }

            When("Calling it with a finite stream") {
                Then("Returns an expected value") {
                    client.clientStream(flowOf(1, 2, 3, 4, 5)) shouldBe "Hello 15"
                }
            }

            When("Calling it with an empty stream") {
                Then("Returns an expected value") {
                    client.clientStream(emptyFlow()) shouldBe "Hello 0"
                }
            }

            When("Calling it with a finite stream only taking some elements") {
                Then("Returns an expected value") {
                    client.clientStream(flowOf(1, 2, 3, 4, 5).take(2)) shouldBe "Hello 3"
                }
            }
        }

        And("A client stream call not collecting the flow") {
            beforeTest {
                register(object: BaseTestService() {
                    override suspend fun clientStream(flow: Flow<Int>): String {
                        return "Hello World!"
                    }
                })
            }

            When("Calling it with any flow") {
                Then("Returns the same value and doesn't subscribe the client stream") {
                    client.clientStream(flowOf(1, 2, 3, 4, 5).onStart { error("Should not start") }) shouldBe "Hello World!"
                    client.clientStream(emptyFlow<Int>().onStart { error("Should not start") }) shouldBe "Hello World!"
                    client.clientStream(flowOf(1, 2, 3, 4, 5).take(2).onStart { error("Should not start") }) shouldBe "Hello World!"
                }
            }
        }

        xAnd("A client stream call that returns before the stream completes") {

        }

        And("A server stream call") {
            beforeTest {
                register(object: BaseTestService() {
                    override suspend fun serverStream(request: Int): Flow<String> {
                        return if (request < 1) {
                            emptyFlow()
                        } else {
                            (1..request).asFlow().map { "$it" }
                        }
                    }
                })
            }

            When("Calling with any integer") {
                Then("A flow with elements from 1 through the value is returned") {
                    client.serverStream(5).toList() shouldContainExactly listOf("1", "2", "3", "4", "5")
                    client.serverStream(0).toList() shouldHaveSize 0
                    client.serverStream(2).take(2).toList() shouldContainExactly listOf("1", "2")
                }
            }
        }

        And("A bidirection stream call") {
            beforeTest {
                register(object: BaseTestService() {
                    override suspend fun bidiStream(flow: Flow<Int>): Flow<String> {
                        return flow.map { it.toString() }
                    }
                })
            }

            When("Calling with an upstream flow of integers") {
                Then("Returns the same flow mapped to strings") {
                    client.bidiStream(flowOf(1, 2, 3, 4, 5)).toList() shouldContainExactly listOf("1", "2", "3", "4", "5")
                    client.bidiStream(emptyFlow()).toList() shouldHaveSize 0
                    client.bidiStream(flowOf(1, 2, 3, 4, 5).take(2)).toList() shouldContainExactly listOf("1", "2")
                }
            }
        }
    }

}) {


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
                val singleCall = SingleCallDescription(
                    ServiceCallIdentifier(serviceIdentifier, "singleCall"),
                    Unit.serializer(),
                    String.serializer(),
                    RPCErrorSerializer(),
                )

                val clientStream = ColdUpstreamCallDescription(
                    ServiceCallIdentifier(serviceIdentifier, "clientStream"),
                    Unit.serializer(),
                    Int.serializer(),
                    String.serializer(),
                    RPCErrorSerializer(),
                )

                val serverStream = ColdDownstreamCallDescription(
                    ServiceCallIdentifier(serviceIdentifier, "serverStream"),
                    Int.serializer(),
                    String.serializer(),
                    RPCErrorSerializer(),
                )

                val bidiStream = ColdBistreamCallDescription(
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

// @OptIn(ExperimentalCoroutinesApi::class)
// internal class _AscentionProtocolTest {
//     private val testScope = TestCoroutineScope()
//     private lateinit var registry: ServiceRegistry
//     private lateinit var client: TestService
//
//     companion object {
//         @BeforeAll
//         @JvmStatic
//         fun preSetup() {
//             Logger.setLevel(LoggingLevel.Trace)
//         }
//     }
//
//     @BeforeEach
//     fun setup() {
//         val connection = LoopbackConnection(testScope)
//         registry = DefaultServiceRegistry()
//
//         val protocol = AscensionRPCProtocol.Factory(registry).create(connection)
//         client = TestService.Client(protocol)
//     }
//
//     @AfterEach
//     fun teardown() {
//         testScope.cleanupTestCoroutines()
//     }
//
//     @Test
//     fun `SingleCall - Succeeds`() = testScope.runBlockingTest {
//         register(
//             object: BaseTestService() {
//                 override suspend fun singleCall(): String {
//                     return "Hello world!"
//                 }
//             }
//         )
//
//         try {
//             println(client.singleCall())
//         } catch (t: Throwable) {
//             println("WTF: $t")
//         }
//         assertEquals("Hello world!", client.singleCall())
//         assertEquals("Hello world!", client.singleCall())
//     }
//
//     @Test
//     fun `ClientStream - Waits for the end of client flow`() = testScope.runBlockingTest {
//         register(
//             object: BaseTestService() {
//                 override suspend fun clientStream(flow: Flow<Int>): String {
//                     return "Hello " + flow.fold(0) { acc, value -> acc + value }.toString()
//                 }
//             }
//         )
//
//         assertEquals("Hello 15", client.clientStream(flowOf(1, 2, 3, 4, 5)))
//         assertEquals("Hello 0", client.clientStream(flowOf()))
//         assertEquals("Hello 3", client.clientStream(flowOf(1, 2, 3, 4, 5).take(2)))
//     }
//
//     @Test
//     fun `ClientStream - Not collecting client stream`() = testScope.runBlockingTest {
//         register(
//             object: BaseTestService() {
//                 override suspend fun clientStream(flow: Flow<Int>): String {
//                     return "Hello World!"
//                 }
//             }
//         )
//
//         assertEquals("Hello World!", client.clientStream(flowOf(1, 2, 3, 4, 5).onStart { error("Should not start") }))
//         assertEquals("Hello World!", client.clientStream(emptyFlow<Int>().onStart { error("Should not start") }))
//         assertEquals("Hello World!", client.clientStream(flowOf(1, 2, 3, 4, 5).take(2).onStart { error("Should not start") }))
//     }
//
//     @Test
//     fun `ClientStream - Return before client stream completes`() = testScope.runBlockingTest {
//         val result = Channel<Int>()
//
//         register(
//             object: BaseTestService() {
//                 override suspend fun clientStream(flow: Flow<Int>): String {
//                     val wait = CompletableDeferred<Unit>()
//                     coroutineScope {
//                         this.launch {
//                             val first = flow.first()
//                             wait.complete(Unit)
//                             delay(1_000)
//                             result.send(flow.fold(first) { acc, value -> acc + value })
//                         }
//                     }
//                     return "Hello World!"
//                 }
//             }
//         )
//
//         val resultFlow = result.consumeAsFlow()
//
//         assertEquals("Hello World!", client.clientStream(flowOf(1, 2, 3, 4, 5)).also { println("R1: $it") })
//         assertEquals(15, resultFlow.first().also { println("R2: $it") })
//
//         // assertEquals("Hello World!", client.clientStream(flowOf()))
//         // assertEquals(0, resultFlow.first())
//         //
//         // assertEquals("Hello World!", client.clientStream(flowOf(1, 2, 3, 4, 5).take(2)))
//         // assertEquals(3, resultFlow.first())
//     }
//
//     @Test
//     fun `ServerStream - Sends all flow events`() = testScope.runBlockingTest {
//         register(
//             object: BaseTestService() {
//                 override suspend fun serverStream(request: Int): Flow<String> {
//                     return if (request < 1) {
//                         emptyFlow()
//                     } else {
//                         (1..request).asFlow().map { "$it" }
//                     }
//                 }
//             }
//         )
//
//         assertEquals(listOf("1", "2", "3", "4", "5"), client.serverStream(5).toList())
//         assertEquals(emptyList<String>(), client.serverStream(0).toList())
//         assertEquals(listOf("1", "2"), client.serverStream(2).take(2).toList())
//     }
//
//     @Test
//     fun `Bistream - Sends and receives all flow events`() = testScope.runBlockingTest {
//         register(
//             object: BaseTestService() {
//                 override suspend fun bidiStream(flow: Flow<Int>): Flow<String> {
//                     return flow.map { it.toString() }
//                 }
//             }
//         )
//
//
//     }
//
//     private fun register(testService: TestService) {
//         registry.register(
//             TestService.Descriptor.describe(
//                 testService
//             )
//         )
//     }
// }