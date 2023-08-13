package org.brightify.hyperdrive

import kotlin.test.Test

class LifecycleDescriptionTest {

    @Test
    fun printDumpTree() {
        val root = Lifecycle("RootVM").apply {
            addChild(Lifecycle("Child1VM").apply {
                addChild(Lifecycle("GrandChild1VM"))
                addChild(Lifecycle("GrandChild2VM"))
            })
            addChild(Lifecycle("Child2VM"))
        }

        println(root.dumpTree())
    }

    @Test
    fun printViewModelLifecycleDumpTree() {
        val root = RootViewModel()

        println(root.lifecycle.dumpTree())
    }

    private class RootViewModel: BaseViewModel() {
        val child1 by managed(Child1ViewModel())
        val child2 by managed(Child2ViewModel())
    }

    private class Child1ViewModel: BaseViewModel() {
        val grandChild1 by managed(GrandChild1ViewModel())
        val grandChild2 by managed(GrandChild2ViewModel())
    }
    private class Child2ViewModel: BaseViewModel()
    private class GrandChild1ViewModel: BaseViewModel()
    private class GrandChild2ViewModel: BaseViewModel()
}
