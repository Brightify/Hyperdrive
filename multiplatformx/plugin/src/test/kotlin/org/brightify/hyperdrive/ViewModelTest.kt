package org.brightify.hyperdrive

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ViewModelTest {

    @Test
    fun testViewModel() {
        val serviceSource = SourceFile.kotlin("TestViewModel.kt", """
            import org.brightify.hyperdrive.multiplatformx.ViewModel
            import org.brightify.hyperdrive.multiplatformx.BaseViewModel
            import kotlinx.coroutines.flow.StateFlow
            
            @ViewModel
            class TestViewModel: BaseViewModel() {
               
                var x: String? by published(null)
                
//                val observeX: StateFlow<String?> by observe(this::x)
                
                init {
                    println(observeX)
                }
            }
        """.trimIndent())


        val result = KotlinCompilation().apply {
            sources = listOf(serviceSource)

            compilerPlugins = listOf<ComponentRegistrar>(
                MultiplatformXComponentRegistrar()
            )

            useIR = true

            includeRuntime = true
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()

        println(result.generatedFiles)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

//
//        val secondResult = KotlinCompilation().apply {
//            sources = listOf(usage)
//
//            useIR = true
//            includeRuntime = true
//            inheritClassPath = true
//            messageOutputStream = System.out
//
//            this.cla
//        }

        val viewModelClass = result.classLoader.loadClass("TestViewModel")
        val ctor = viewModelClass.getDeclaredConstructor()
        val viewModel = ctor.newInstance()
        println(viewModel)
    }

}