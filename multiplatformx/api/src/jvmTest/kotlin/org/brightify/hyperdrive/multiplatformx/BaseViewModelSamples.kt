package org.brightify.hyperdrive.multiplatformx

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.withTestContext
import kotlin.test.Test

class BaseViewModelSamples {

    @OptIn(ExperimentalCoroutinesApi::class)
    val testScope = TestCoroutineScope()

    @Test
    fun managedTest() = runBlocking {
        class Child: BaseViewModel()

        class Parent: BaseViewModel() {
            val child by managed(Child())
        }

        val parent = Parent()
        parent.lifecycle.attach(testScope)
        delay(1000)
        parent.lifecycle.detach()
        delay(1000)
        parent.lifecycle.attach(testScope)
        delay(1000)
        parent.lifecycle.detach()
        delay(1000)
        parent.lifecycle.attach(testScope)
        delay(1000)
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