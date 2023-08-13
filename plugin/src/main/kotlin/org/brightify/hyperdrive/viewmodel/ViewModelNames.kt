package org.brightify.hyperdrive.viewmodel

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.reflect.KClass

object ViewModelNames {
    object Annotation {
        val viewModel = classIdOf("org.brightify.hyperdrive", "ViewModel")
    }

    object Coroutines {
        val stateFlowClassId = classIdOf("kotlinx.coroutines.flow", "StateFlow")
        val stateFlow = stateFlowClassId.asSingleFqName()

        val mutableStateFlowClassId = classIdOf("kotlinx.coroutines.flow", "MutableStateFlow")
        val mutableStateFlow = mutableStateFlowClassId.asSingleFqName()
    }

    object API {
        val baseViewModel = classIdOf("org.brightify.hyperdrive", "BaseViewModel")
        val manageableViewModel = classIdOf("org.brightify.hyperdrive", "ManageableViewModel")
        val baseObservableObject = classIdOf("org.brightify.hyperdrive", "BaseObservableObject")
        val observableProperty = classIdOf("org.brightify.hyperdrive.property", "ObservableProperty")
        val mutableViewModelProperty = classIdOf("org.brightify.hyperdrive.property", "MutableObservableProperty")
    }

    object Kotlin {
        val lazy = classIdOf("kotlin", "Lazy")

        object Lazy {
            val value = Name.identifier("value")
        }
    }

    private fun classIdOf(packageName: String, className: String): ClassId {
        return ClassId(FqName(packageName), Name.identifier(className))
    }
}
