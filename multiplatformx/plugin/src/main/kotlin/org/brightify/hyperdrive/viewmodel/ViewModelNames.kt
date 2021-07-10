package org.brightify.hyperdrive.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.reflect.KClass

object ViewModelNames {
    object Annotation {
        val viewModel = FqName("org.brightify.hyperdrive.multiplatformx.ViewModel")
    }

    object Coroutines {
        val stateFlowClassId = classIdOf(StateFlow::class)
        val stateFlow = stateFlowClassId.asSingleFqName()

        val mutableStateFlowClassId = classIdOf(MutableStateFlow::class)
        val mutableStateFlow = mutableStateFlowClassId.asSingleFqName()
    }

    object API {
        val baseViewModel = classIdOf(BaseViewModel::class)


    }

    object Kotlin {
        val lazy = FqName(kotlin.Lazy::class.qualifiedName!!)

        object Lazy {
            val value = Name.identifier(kotlin.Lazy<*>::value.name)
        }
    }

    private fun <T: Any> classIdOf(cls: KClass<T>): ClassId
            = ClassId(FqName(cls.java.`package`.name), Name.identifier(cls.java.simpleName))
}