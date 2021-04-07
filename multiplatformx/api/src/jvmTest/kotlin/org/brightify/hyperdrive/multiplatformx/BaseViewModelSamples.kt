package org.brightify.hyperdrive.multiplatformx

import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.withTestContext
import kotlin.test.Test

class BaseViewModelSamples {

    val testScope = TestCoroutineScope()

    @Test
    fun managedTest() {
        class Child: BaseViewModel()

        class Parent: BaseViewModel() {
            val child by managed(Child())
        }

        val parent = Parent()
        parent.lifecycle.attach(testScope)
        parent.lifecycle.detach()
        parent.lifecycle.attach(testScope)
    }

    @Test
    fun notifyObjectWillChange() {
        class Sample: BaseViewModel() {
            var name: String? = null
                private set

            fun rename(newName: String) {
                notifyObjectWillChange()

                name = newName
            }
        }
    }
}