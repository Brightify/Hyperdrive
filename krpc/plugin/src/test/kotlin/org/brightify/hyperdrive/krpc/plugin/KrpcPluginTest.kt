package org.brightify.hyperdrive.krpc.plugin

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.brightify.hyperdrive.krpc.MutableServiceRegistry
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.SerializationFormat
import org.brightify.hyperdrive.krpc.description.ServiceDescriptor
import org.brightify.hyperdrive.krpc.impl.DefaultServiceRegistry
import org.brightify.hyperdrive.krpc.impl.JsonCombinedSerializer
import org.brightify.hyperdrive.krpc.impl.SerializerRegistry
import org.brightify.hyperdrive.krpc.protocol.KRPCNode
import org.brightify.hyperdrive.krpc.protocol.ascension.AscensionRPCProtocol
import org.brightify.hyperdrive.krpc.protocol.ascension.RPCHandshakePerformer
import org.brightify.hyperdrive.krpc.test.LoopbackConnection
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.reflect.KClass
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredFunctions
import kotlin.test.Test
import kotlin.test.assertEquals

class KrpcPluginTest {

    private val testScope = TestCoroutineScope()
    private lateinit var registry: MutableServiceRegistry
    private lateinit var node: KRPCNode

    @BeforeEach
    fun setup() {
        val connection = LoopbackConnection(testScope)
        registry = DefaultServiceRegistry()

        val serializerRegistry = SerializerRegistry(
            JsonCombinedSerializer.Factory()
        )
        node = KRPCNode(
            registry,
            object: RPCHandshakePerformer {
                override suspend fun performHandshake(connection: RPCConnection): RPCHandshakePerformer.HandshakeResult {
                    return RPCHandshakePerformer.HandshakeResult.Success(
                        serializerRegistry.transportFrameSerializerFactory.create(SerializationFormat.Text.Json),
                        AscensionRPCProtocol.Factory(),
                    )
                }
            },
            serializerRegistry.payloadSerializerFactory,
            listOf(),
            connection,
        )
    }

    @AfterEach
    fun teardown() {
        testScope.cleanupTestCoroutines()
    }

    @Test
    fun testKrpcPlugin() {
        val serviceSource = SourceFile.kotlin("ProcessorTestService.kt", """
            @file:UseSerializers(UuidSerializer::class)

            import kotlinx.coroutines.flow.Flow
            import kotlinx.coroutines.flow.fold
            import org.brightify.hyperdrive.krpc.api.EnableKRPC
            import org.brightify.hyperdrive.krpc.api.Error
            import org.brightify.hyperdrive.krpc.error.RPCNotFoundError
            import kotlinx.coroutines.flow.asFlow
            import kotlinx.coroutines.flow.map
            import org.brightify.hyperdrive.krpc.RPCTransport
            import kotlinx.serialization.UseSerializers
            import kotlinx.serialization.KSerializer
            import kotlinx.serialization.descriptors.SerialDescriptor
            import kotlinx.serialization.encoding.Decoder
            import kotlinx.serialization.encoding.Encoder
            import kotlinx.serialization.builtins.serializer
            
            class Uuid(val value: String)
            
            class UuidSerializer: KSerializer<Uuid> {
                override val descriptor: SerialDescriptor = String.serializer().descriptor
                override fun serialize(encoder: Encoder, value: Uuid) {
                    encoder.encodeString(value.value)
                }
                override fun deserialize(decoder: Decoder): Uuid {
                    return Uuid(decoder.decodeString())
                }
            }
            
            @EnableKRPC
            interface ProcessorTestService {
                @Error(RPCNotFoundError::class)
                suspend fun testedSingleCall(parameter1: Int): String
                
                suspend fun testedSingleCall2(): String
                
                suspend fun clientStream(flow: Flow<Int>): String
                
                suspend fun serverStream(request: Int): Flow<String>
                
                suspend fun bidiStream(flow: Flow<Int>): Flow<String>
                
                suspend fun singleCallWithAdditionalSerializer(normalParameter: Int, additionalParameter: Uuid)     
            }
            
            class DefaultProcessorTestService: ProcessorTestService {
                override suspend fun testedSingleCall(parameter1: Int): String {
                    return "Hello ${"$"}parameter1"
                }
                
                override suspend fun testedSingleCall2(): String {
                    return "Hello World!"
                }
                
                override suspend fun clientStream(flow: Flow<Int>): String {
                    return flow.fold(0) { acc, value -> acc + value }.toString()
                }
                
                override suspend fun serverStream(request: Int): Flow<String> {
                    return (1..request).asFlow().map { "Hello ${"$"}it" } 
                }
                
                override suspend fun bidiStream(flow: Flow<Int>): Flow<String> {
                    return flow.map { println("AB: ${"$"}it"); it.toString() }
                }
                
                override suspend fun singleCallWithAdditionalSerializer(normalParameter: Int, additionalParameter: Uuid) {
                }
            }
            
            fun x() {
                val x = ProcessorTestService.Client(null as RPCTransport)
                val b = ProcessorTestService.Descriptor
                val c = ProcessorTestService.Descriptor.Call
            }
        """.trimIndent())

        val result = KotlinCompilation().apply {
            sources = listOf(serviceSource) //, serviceClientSource, serviceDescriptorSource)

            compilerPlugins = listOf<ComponentRegistrar>(
                KrpcComponentRegistrar()
            )
            commandLineProcessors = listOf(
                KrpcCommandLineProcessor()
            )
            pluginOptions = listOf(
                PluginOption(
                    KrpcCommandLineProcessor.pluginId,
                    KrpcCommandLineProcessor.Options.enabled.optionName,
                    "true"
                ),
                PluginOption(
                    KrpcCommandLineProcessor.pluginId,
                    KrpcCommandLineProcessor.Options.printIR.optionName,
                    "true"
                ),
                PluginOption(
                    KrpcCommandLineProcessor.pluginId,
                    KrpcCommandLineProcessor.Options.printKotlinLike.optionName,
                    "true"
                )
            )

            useIR = true
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()

        println(result.generatedFiles)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val serviceClass = result.classLoader.loadClass("DefaultProcessorTestService").kotlin
        val serviceInstance = serviceClass.createInstance()

        val descriptorClass = result.classLoader.loadClass("ProcessorTestService\$Descriptor").kotlin as KClass<ServiceDescriptor<Any>>
        val descriptorInstance = descriptorClass.objectInstance!!
        
        registry.register(descriptorInstance.describe(serviceInstance))

        val clientClass = result.classLoader.loadClass("ProcessorTestService\$Client").kotlin

        testScope.runBlockingTest {
            launch { node.run() }
            val clientInstance = clientClass.constructors.first().call(node.transport())
            val response = clientClass.declaredFunctions.single { it.name == "testedSingleCall" }.callSuspend(clientInstance, 10)
            println(response)
            assertEquals("Hello 10", response)

            val response2 = clientClass.declaredFunctions.single { it.name == "testedSingleCall2" }.callSuspend(clientInstance)
            println(response2)
            assertEquals("Hello World!", response2)

            val response3 = clientClass.declaredFunctions.single { it.name == "clientStream" }.callSuspend(clientInstance, flowOf(1, 2, 3, 4, 5))
            println(response3)
            assertEquals("15", response3)

            val response4Flow = clientClass.declaredFunctions.single { it.name == "serverStream" }.callSuspend(clientInstance, 5) as Flow<String>
            val response4 = response4Flow.fold(emptyList<String>()) { acc, value ->
                acc + value
            }.joinToString(", ")
            println(response4)
            assertEquals("Hello 1, Hello 2, Hello 3, Hello 4, Hello 5", response4)

            val response5Flow = clientClass.declaredFunctions.single { it.name == "bidiStream" }.callSuspend(clientInstance, flowOf(1, 2, 3, 4, 5)) as Flow<String>
            val response5 = response5Flow.fold(emptyList<String>()) { acc, value ->
                acc + value
            }.joinToString(", ")
            println(response5)
            assertEquals("1, 2, 3, 4, 5", response5)

            node.close()
        }
    }
}