package org.brightify.hyperdrive.krpc

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.symbolProcessors
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test

internal class ServiceProcessorTest {

    @Test
    fun `run`() {
        val serviceSource = SourceFile.kotlin("ProcessorTestService.kt", """
            package org.brightify.hyperdrive.krpc.processor
            
            import kotlinx.coroutines.flow.Flow
            import org.brightify.hyperdrive.krpc.api.Service
    
            @Service
            interface ProcessorTestService {
                suspend fun singleCall(request: Int): String
    
                suspend fun clientStream(flow: Flow<Int>): String
    
                suspend fun serverStream(request: Int): Flow<String>
    
                suspend fun bidiStream(flow: Flow<Int>): Flow<String>
            }
            
            fun x() {
                val a = ProcessorTestServiceClient::class
                val b = ProcessorTestServiceDescriptor::class
            }
        """.trimIndent())

        val result = KotlinCompilation().apply {
            sources = listOf(serviceSource)

//            compilerPlugins = listOf<ComponentRegistrar>(KotlinSymbolProcessingComponentRegistrar())
            symbolProcessors = listOf(ServiceProcessor())

            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()

        println(result.generatedFiles)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

}