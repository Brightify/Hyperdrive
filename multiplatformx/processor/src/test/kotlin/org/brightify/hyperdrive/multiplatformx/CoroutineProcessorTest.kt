package org.brightify.hyperdrive.multiplatformx

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.symbolProcessors
import kotlin.test.Test
import kotlin.test.assertEquals

internal class CoroutineProcessorTest {
    @Test
    fun `run`() {
        val serviceSource = SourceFile.kotlin("ProcessorTestService.kt", """
            package org.brightify.hyperdrive.swiftcoroutines.test
            
            import kotlinx.coroutines.flow.Flow
            import org.brightify.hyperdrive.swiftcoroutines.EnhanceMultiplatform
            import org.brightify.hyperdrive.swiftcoroutines.Presenter
            import org.brightify.hyperdrive.swiftcoroutines.ObservableObject
    
            @EnhanceMultiplatform
            interface ProcessorTestService {
                class InnerClass {
                }

                val nullableTest: Flow<Long?>

                fun serverStream(request: InnerClass): Flow<String>
            }
            
            @Presenter
            class PresetPresenter: ObservableObject() {
            }
            
            @Presenter
            class TestedPresenter: ObservableObject() {
                var activeTab: Tab by published(Tab.Presets)
            
                val presetsInstance = PresetPresenter()
            
                fun presets(): PresetPresenter = PresetPresenter()
                 
                fun x() {
            
                }
            
                enum class Tab {
                    Presets,
                    Routing,
                    Settings,
                }
            }
            
        """.trimIndent())

        val result = KotlinCompilation().apply {
            sources = listOf(serviceSource)

//            compilerPlugins = listOf<ComponentRegistrar>(KotlinSymbolProcessingComponentRegistrar())

            symbolProcessors = listOf(CoroutineProcessor())

            includeRuntime = true
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()

        println(result.generatedFiles)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }
}