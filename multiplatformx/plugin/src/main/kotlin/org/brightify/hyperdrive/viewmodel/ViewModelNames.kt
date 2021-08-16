package org.brightify.hyperdrive.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.brightify.hyperdrive.multiplatformx.BaseViewModel
import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
import org.brightify.hyperdrive.multiplatformx.property.ObservableProperty
import org.brightify.hyperdrive.multiplatformx.property.MutableObservableProperty
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.reflect.KClass

object ViewModelNames {
    object Annotation {
        val viewModel = FqName("org.brightify.hyperdrive.multiplatformx.ViewModel")
        val noAutoObserve = FqName("org.brightify.hyperdrive.multiplatformx.compose.NoAutoObserve")
    }

    object Coroutines {
        val stateFlowClassId = classIdOf(StateFlow::class)
        val stateFlow = stateFlowClassId.asSingleFqName()

        val mutableStateFlowClassId = classIdOf(MutableStateFlow::class)
        val mutableStateFlow = mutableStateFlowClassId.asSingleFqName()
    }

    object API {
        val baseViewModel = classIdOf(BaseViewModel::class)
        val manageableViewModel = classIdOf(ManageableViewModel::class)
        val observableProperty = classIdOf(ObservableProperty::class)
        val mutableViewModelProperty = classIdOf(MutableObservableProperty::class)
    }

    object Compose {
        val composable = FqName("androidx.compose.runtime.Composable")
        val state = FqName("androidx.compose.runtime.State")
        val stateValue = "value"
        val observeAsState = FqName("org.brightify.hyperdrive.multiplatformx.compose.observeAsState")
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