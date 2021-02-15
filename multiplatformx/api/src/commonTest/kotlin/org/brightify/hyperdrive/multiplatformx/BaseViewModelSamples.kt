package org.brightify.hyperdrive.multiplatformx

import kotlin.test.Test

class BaseViewModelSamples {

    @Test
    fun managed() {
        class Child: BaseViewModel()

        class Parent: BaseViewModel() {
            val child by managed(Child())
        }
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