package org.brightify.hyperdrive.krpc.impl

import io.kotest.core.spec.style.BehaviorSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineExceptionHandler
import kotlinx.coroutines.test.TestCoroutineScope
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.LoggingLevel
import org.brightify.hyperdrive.krpc.MutableServiceRegistry
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.protocol.DefaultRPCNode
import org.brightify.hyperdrive.krpc.protocol.ascension.AscensionRPCProtocol
import org.brightify.hyperdrive.krpc.protocol.ascension.RPCHandshakePerformer
import org.brightify.hyperdrive.krpc.test.LoopbackConnection

@OptIn(ExperimentalCoroutinesApi::class)
class SessionNodeExtensionTest: BehaviorSpec({

    val testScope = TestCoroutineScope(TestCoroutineExceptionHandler())

    beforeSpec {
        Logger.configure { setMinLevel(LoggingLevel.Trace) }
    }

    afterTest {
        testScope.cleanupTestCoroutines()
    }

    Given("KRPCNode with SessionNodeExtension") {
        lateinit var registry: MutableServiceRegistry
        lateinit var connection: RPCConnection
        lateinit var client: KRPCNodeTest.TestService

        fun register(testService: KRPCNodeTest.TestService) {
            registry.register(
                KRPCNodeTest.TestService.Descriptor.describe(
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
                emptyList(),
                registry,
            ).create(connection)
            testScope.launch { node.run { } }
            val transport = node.transport
            client = KRPCNodeTest.TestService.Client(transport)
        }

        afterTest {
            connection.close()
        }
    }

})