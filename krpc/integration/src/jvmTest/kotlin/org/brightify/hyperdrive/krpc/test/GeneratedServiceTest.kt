package org.brightify.hyperdrive.krpc.test

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.data.row
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.withIndex
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.LoggingLevel
import org.brightify.hyperdrive.krpc.application.CallLoggingNodeExtension
import org.brightify.hyperdrive.krpc.client.impl.KRPCClient
import org.brightify.hyperdrive.krpc.client.impl.ktor.WebSocketClient
import org.brightify.hyperdrive.krpc.error.InternalServerError
import org.brightify.hyperdrive.krpc.impl.DefaultServiceRegistry
import org.brightify.hyperdrive.krpc.impl.JsonCombinedSerializer
import org.brightify.hyperdrive.krpc.impl.SerializerRegistry
import org.brightify.hyperdrive.krpc.error.ConnectionClosedException
import org.brightify.hyperdrive.krpc.server.impl.KRPCServer
import org.brightify.hyperdrive.krpc.server.impl.ktor.KtorServerFrontend
import org.brightify.hyperdrive.krpc.session.SessionContextKeyRegistry
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(ObsoleteCoroutinesApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GeneratedServiceTest: BehaviorSpec({
    Logger.configure { setMinLevel(LoggingLevel.Trace) }

    lateinit var server: KRPCServer
    val serviceImpl = object: BasicTestService {
        override suspend fun multiplyByTwo(source: Int): Int {
            return source * 2
        }

        override suspend fun multiply(source: Int, multiplier: Int): Int {
            return source * multiplier
        }

        override suspend fun singleCallError() {
            throw IllegalArgumentError("source cannot be zero")
        }

        override suspend fun singleCallUnexpectedError() {
            error("This is an unexpected error for sure.")
        }

        override suspend fun singleCallClosingConnection() {
            server.connections.single().close()
            Logger("BasicTestService").debug { "Did close connection from service" }
        }

        override suspend fun sum(stream: Flow<Int>): Int {
            return stream.fold(0) { accumulator, value -> accumulator + value }
        }

        override suspend fun sumWithInitial(initialValue: Int, stream: Flow<Int>): Int {
            return stream.fold(initialValue) { accumulator, value -> accumulator + value }
        }

        override suspend fun clientStreamError(stream: Flow<Unit>) {
            try {
                stream.collect()
                error("Expected exception not thrown!")
            } catch (e: IllegalArgumentError) {
                throw e
            }
        }

        override suspend fun timer(count: Int): Flow<Int> {
            if (count <= 0) {
                return emptyFlow()
            }
            return ticker(1, 0).receiveAsFlow().withIndex().map { it.index }.take(count)
        }

        override suspend fun multiplyEachByTwo(stream: Flow<Int>): Flow<Int> {
            return stream.map { it * 2 }
        }
    }

    val testScope = CoroutineScope(EmptyCoroutineContext)

    beforeSpec {
    }

    afterContainer {
    }

    val client = lazy {
        val serializers = SerializerRegistry(
            JsonCombinedSerializer.Factory()
        )

        val registry = DefaultServiceRegistry()
        registry.register(BasicTestService.Descriptor.describe(serviceImpl))

        server = KRPCServer(
            KtorServerFrontend(),
            testScope,
            serializers.transportFrameSerializerFactory,
            serializers.payloadSerializerFactory,
            registry,
            SessionContextKeyRegistry.Empty,
            additionalExtensions = listOf(
                CallLoggingNodeExtension.Factory(Logger(KRPCServer::class)),
            )
        ).also { it.start() }

        KRPCClient(
            WebSocketClient(),
            testScope,
            serializers,
            registry,
            additionalExtensions = listOf(
                CallLoggingNodeExtension.Factory(Logger(KRPCClient::class)),
            )
        ).also { it.start() }
    }

    listOf(client).forEach { lazyTransport ->
        val transport = lazyTransport.value
        Given("An RPCTransport") {
            val service = BasicTestService.Client(transport)
            And("Basic Test Service") {
                When("Running single call") {
                    Then("`multiplyByTwo` returns input times two") {
                        checkAll<Int> { input ->
                            service.multiplyByTwo(input) shouldBe input * 2
                        }
                    }

                    Then("`multiply` returns first parameter times second parameter") {
                        checkAll<Int, Int> { lhs, rhs ->
                            service.multiply(lhs, rhs) shouldBe lhs * rhs
                        }
                    }

                    Then("`singleCallError` throws expected error") {
                        shouldThrowExactly<IllegalArgumentError> {
                            service.singleCallError()
                        }
                    }

                    Then("`singleCallUnexpectedError` throws InternalServerError") {
                        shouldThrowExactly<InternalServerError> {
                            service.singleCallUnexpectedError()
                        }
                    }

                    Then("`singleCallClosingConnection` throws ConnectionClosedException") {
                        shouldThrowExactly<ConnectionClosedException> {
                            service.singleCallClosingConnection()
                        }
                        service.multiplyByTwo(2) shouldBe 2 * 2
                    }
                }

                When("Running upstream call") {
                    Then("`sum` returns sum of all upstream flow events") {
                        listOf(
                            row(21, flowOf(1, 2, 3, 4, 5, 6)),
                            row(0, emptyFlow<Int>()),
                        ).forEach { (sum, flow) ->
                            service.sum(flow) shouldBe sum
                        }
                    }

                    Then("`sumWithInitial` returns sum of all upstream flow events and an initial value") {
                        listOf(
                            row(30, 9, flowOf(1, 2, 3, 4, 5, 6)),
                            row(0, -9, flowOf(3, 3, 3)),
                            row(3, 2, flowOf(1, 2, 3).take(1)),
                        ).forEach { (sum, initial, flow) ->
                            service.sumWithInitial(initial, flow) shouldBe sum
                        }
                    }

                    Then("`clientStreamError` fails") {
                        shouldThrowExactly<IllegalArgumentError> {
                            service.clientStreamError(flow { throw IllegalArgumentError("Expected error") })
                        }
                    }
                }

                When("Running downstream call") {
                    Then("`timer` returns flow with incrementing elements up to requested count") {
                        listOf(
                            row(6, listOf(0, 1, 2, 3, 4, 5)),
                            row(0, emptyList()),
                            row(1, listOf(0)),
                        ).forEach { (input, expectedResult) ->
                            service.timer(input).toList() shouldContainExactly expectedResult
                        }
                    }
                }

                When("Running bistream call") {
                    Then("Each element is multiplied by two") {
                        checkAll<List<Int>> { input ->
                            service.multiplyEachByTwo(input.asFlow()).toList() shouldContainExactly input.map { it * 2 }
                        }
                    }
                }
            }
        }
    }

    // // @Test
    // fun `perform generated bistream call`() = runBlocking {
    //     val service = BasicTestService.Client(client)
    //
    //     assertEquals(42, service.multiplyEachByTwo(flowOf(1, 2, 3, 4, 5, 6)).reduce { accumulator, value -> accumulator + value })
    // }
})