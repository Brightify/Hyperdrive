package org.brightify.hyperdrive.krpc.plugin

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.brightify.hyperdrive.krpc.api.RPCProtocol
import org.brightify.hyperdrive.krpc.api.ServiceDescriptor
import org.brightify.hyperdrive.krpc.api.impl.AscensionRPCProtocol
import org.brightify.hyperdrive.krpc.api.impl.DefaultServiceRegistry
import org.brightify.hyperdrive.krpc.api.impl.ServiceRegistry
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
    private lateinit var registry: ServiceRegistry
    private lateinit var protocol: RPCProtocol

    @BeforeEach
    fun setup() {
        val connection = LoopbackConnection(testScope)
        registry = DefaultServiceRegistry()

        protocol = AscensionRPCProtocol.Factory(registry, testScope, testScope).create(connection)
    }

    @AfterEach
    fun teardown() {
        testScope.cleanupTestCoroutines()
    }

    @Test
    fun testKrpcPlugin() {
        val serviceSource = SourceFile.kotlin("ProcessorTestService.kt", """
            import kotlinx.coroutines.flow.Flow
            import kotlinx.coroutines.flow.fold
            import org.brightify.hyperdrive.krpc.api.EnableKRPC
            import org.brightify.hyperdrive.krpc.api.Error
            import org.brightify.hyperdrive.krpc.api.error.RPCNotFoundError
            import kotlinx.coroutines.flow.asFlow
            import kotlinx.coroutines.flow.map
            
            @EnableKRPC
            interface ProcessorTestService {
                @Error(RPCNotFoundError::class)
                suspend fun testedSingleCall(parameter1: Int): String
                
                suspend fun testedSingleCall2(): String
                
                suspend fun clientStream(flow: Flow<Int>): String
                
                suspend fun serverStream(request: Int): Flow<String>
                
                suspend fun bidiStream(flow: Flow<Int>): Flow<String>
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
            }
            
            fun x() {
                // val a = ProcessorTestService.Client()
                // val b = ProcessorTestService.Descriptor
                // val c = ProcessorTestService.Descriptor.Call
            }
        """.trimIndent())

        val result = KotlinCompilation().apply {
            sources = listOf(serviceSource) //, serviceClientSource, serviceDescriptorSource)

            compilerPlugins = listOf<ComponentRegistrar>(
                KrpcComponentRegistrar()
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
        val clientInstance = clientClass.constructors.first().call(protocol)

        testScope.runBlockingTest {
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
        }
    }
}