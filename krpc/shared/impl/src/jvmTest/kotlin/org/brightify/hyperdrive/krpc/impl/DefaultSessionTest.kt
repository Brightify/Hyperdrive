package org.brightify.hyperdrive.krpc.impl

import io.kotest.core.spec.style.BehaviorSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineExceptionHandler
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.LoggingLevel
import org.brightify.hyperdrive.krpc.MutableServiceRegistry
import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.application.RPCNode
import org.brightify.hyperdrive.krpc.application.RPCNodeExtension
import org.brightify.hyperdrive.krpc.extension.SessionNodeExtension
import org.brightify.hyperdrive.krpc.application.impl.DefaultRPCNode
import org.brightify.hyperdrive.krpc.protocol.ascension.AscensionRPCProtocol
import org.brightify.hyperdrive.krpc.application.RPCHandshakePerformer
import org.brightify.hyperdrive.krpc.session.Session
import org.brightify.hyperdrive.krpc.session.impl.DefaultSessionContextKeyRegistry
import org.brightify.hyperdrive.krpc.session.withSession
import org.brightify.hyperdrive.krpc.test.TestConnection
import kotlin.reflect.KClass
import kotlin.test.expect

object DeadlockTestKey : Session.Context.Key<Int> {
    override val qualifiedName: String = "test:deadlock"
    override val serializer: KSerializer<Int> = Int.serializer()
}

class DeadlockDebugExtension(private val identifier: Factory.Identifier): RPCNodeExtension {
    companion object {
        private val logger = Logger(DeadlockDebugExtension::class)
    }


    override suspend fun bind(transport: RPCTransport, contract: RPCNode.Contract) {
    }

    override suspend fun whileConnected() = withSession {
        this.observe(DeadlockTestKey).collect {
            logger.debug { "Value: $it" }

            logger.debug { "Before transaction: <$identifier>" }
            contextTransaction {
                logger.debug { "In transaction: <$identifier>" }
            }
            logger.debug { "After transaction: <$identifier>" }
        }
    }

    class Factory(
        override val identifier: Identifier,
    ): RPCNodeExtension.Factory<DeadlockDebugExtension> {
        class Identifier(override val uniqueIdentifier: String): RPCNodeExtension.Identifier<DeadlockDebugExtension> {
            override val extensionClass: KClass<DeadlockDebugExtension> = DeadlockDebugExtension::class
        }

        override val isRequiredOnOtherSide: Boolean = false

        override fun create(): DeadlockDebugExtension {
            return DeadlockDebugExtension(identifier)
        }
    }
}

class LocalStorageMockPlugin: SessionNodeExtension.Plugin {
    override suspend fun onBindComplete(session: Session) {
        session.contextTransaction {
            set(DeadlockTestKey, 42)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SessionNodeExtensionTest: BehaviorSpec({

    val testScope = TestCoroutineScope(TestCoroutineExceptionHandler())

    beforeSpec {
        Logger.configure {
            destination(object: Logger.Destination {
                override fun log(level: LoggingLevel, throwable: Throwable?, tag: String, message: String) {
                    println("$level: $tag: $message")
                }
            })
            setMinLevel(LoggingLevel.Trace)
        }
    }

    afterTest {
        testScope.cleanupTestCoroutines()
    }

    Given("KRPCNode with SessionNodeExtension") {
        lateinit var registry: MutableServiceRegistry
        lateinit var connection: TestConnection
        lateinit var client: KRPCNodeTest.TestService

        fun register(testService: KRPCNodeTest.TestService) {
            registry.register(
                KRPCNodeTest.TestService.Descriptor.describe(
                    testService
                )
            )
        }

        beforeTest {
            connection = TestConnection(testScope)
            registry = DefaultServiceRegistry()
            val serializers = SerializerRegistry(
                JsonCombinedSerializer.Factory(),
            )
            val leftNode = DefaultRPCNode.Factory(
                RPCHandshakePerformer.NoHandshake(
                    JsonTransportFrameSerializer(),
                    AscensionRPCProtocol.Factory(),
                ),
                serializers.payloadSerializerFactory,
                listOf(
                    SessionNodeExtension.Factory(
                        DefaultSessionContextKeyRegistry(DeadlockTestKey),
                        payloadSerializerFactory = serializers.payloadSerializerFactory,
                        plugins = listOf(),
                    ),
                    DeadlockDebugExtension.Factory(DeadlockDebugExtension.Factory.Identifier("deadlock1")),
                    DeadlockDebugExtension.Factory(DeadlockDebugExtension.Factory.Identifier("deadlock2")),
                    DeadlockDebugExtension.Factory(DeadlockDebugExtension.Factory.Identifier("deadlock3")),
                    DeadlockDebugExtension.Factory(DeadlockDebugExtension.Factory.Identifier("deadlock4")),
                ),
                registry,
            ).create(connection.left)
            testScope.launch { leftNode.run { } }

            val rightNode = DefaultRPCNode.Factory(
                RPCHandshakePerformer.NoHandshake(
                    JsonTransportFrameSerializer(),
                    AscensionRPCProtocol.Factory(),
                ),
                serializers.payloadSerializerFactory,
                listOf(
                    SessionNodeExtension.Factory(
                        DefaultSessionContextKeyRegistry(DeadlockTestKey),
                        payloadSerializerFactory = serializers.payloadSerializerFactory,
                        plugins = listOf(
                            LocalStorageMockPlugin()
                        ),
                    ),
                ),
                DefaultServiceRegistry(),
            ).create(connection.right)
            testScope.launch { rightNode.run { } }

            val transport = rightNode.transport
            client = KRPCNodeTest.TestService.Client(transport)
        }

        Then("Do something")  {
            register(object: KRPCNodeTest.TestService {
                override suspend fun singleCall(): String {
                    return "Hello world"
                }

                override suspend fun clientStream(flow: Flow<Int>): String {
                    TODO("Not yet implemented")
                }

                override suspend fun serverStream(request: Int): Flow<String> {
                    TODO("Not yet implemented")
                }

                override suspend fun bidiStream(flow: Flow<Int>): Flow<String> {
                    TODO("Not yet implemented")
                }

            })
            expect(client.singleCall()) { "Hello world" }
        }

        afterTest {
            connection.close()
        }
    }

})