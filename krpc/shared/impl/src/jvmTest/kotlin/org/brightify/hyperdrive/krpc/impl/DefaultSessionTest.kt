package org.brightify.hyperdrive.krpc.impl

import io.kotest.core.spec.BeforeAny
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.LoggingLevel
import org.brightify.hyperdrive.krpc.MutableServiceRegistry
import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.application.RPCHandshakePerformer
import org.brightify.hyperdrive.krpc.application.RPCNode
import org.brightify.hyperdrive.krpc.application.RPCNodeExtension
import org.brightify.hyperdrive.krpc.application.impl.DefaultRPCNode
import org.brightify.hyperdrive.krpc.extension.SessionNodeExtension
import org.brightify.hyperdrive.krpc.protocol.ascension.AscensionRPCProtocol
import org.brightify.hyperdrive.krpc.session.Session
import org.brightify.hyperdrive.krpc.session.impl.DefaultSessionContextKeyRegistry
import org.brightify.hyperdrive.krpc.session.withSession
import org.brightify.hyperdrive.krpc.test.TestConnection
import kotlin.reflect.KClass

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

    beforeSpec {
        Logger.configure {
            destination(object: Logger.Destination {
                private val start = System.currentTimeMillis()

                override fun log(level: LoggingLevel, throwable: Throwable?, tag: String, message: String) {
                    println("+${System.currentTimeMillis() - start}ms\t| $level: $tag: $message")
                }
            })
            setMinLevel(LoggingLevel.Trace)
        }
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
            connection = TestConnection(this)
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
            launch { leftNode.run { } }

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
            launch { rightNode.run { } }

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
            client.singleCall() shouldBe "Hello world"
            println("check done")

            // FIXME: AfterTest is not being called if we don't close the connection here as if the test kept running indefinitely
            connection.close()
        }

        afterTest {
            // connection.close()
        }
    }
})