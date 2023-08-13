package org.brightify.hyperdrive

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelSamples {

    @Test
    fun managedTest() = runTest {
        class Child: BaseViewModel()

        class Parent: BaseViewModel() {
            val child by managed(Child())
        }
        val root = LifecycleRoot("root")
        val cancelAttach = root.attach(this)
        val parent = Parent()


        root.addChild(parent.lifecycle)
        delay(1000)
        parent.lifecycle.removeFromParent()
        delay(1000)
        root.addChild(parent.lifecycle)
        delay(1000)
        cancelAttach.cancel()
        delay(1000)
        parent.lifecycle.removeFromParent()
        delay(1000)
        root.addChild(parent.lifecycle)
        delay(1000)
        val cancelAttach2 = root.attach(this)
        delay(1000)
        cancelAttach2.cancel()
    }

    @Test
    fun managedListTest() = runTest {
        class Child: BaseViewModel()

        class Parent: BaseViewModel() {
            val child by managedList(listOf(Child()))
        }

        val root = LifecycleRoot("root")
        val cancelAttach = root.attach(this)

        val parent = Parent()

        root.addChild(parent.lifecycle)
        delay(1000)
        parent.lifecycle.removeFromParent()
        delay(1000)
        root.addChild(parent.lifecycle)
        delay(1000)
        cancelAttach.cancel()
        delay(1000)
        val cancelAttach2 = root.attach(this)
        delay(1000)
        cancelAttach2.cancel()
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
