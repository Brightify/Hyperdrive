package org.brightify.hyperdrive.viewmodel

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.runOnFilePostfix
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.name.Name

open class ViewModelIrGenerationExtension(
    private val messageCollector: MessageCollector = MessageCollector.NONE,
): IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val generator = pluginContext.viewModelIrGenerator()

        for (file in moduleFragment.files) {
            generator?.runOnFilePostfix(file)
        }
    }

    private fun IrPluginContext.viewModelIrGenerator(): ViewModelIrGenerator? {
        fun logDisabledReason(reason: String): ViewModelIrGenerator? {
            messageCollector.report(CompilerMessageSeverity.WARNING, "ViewModel Observe Property generator disabled. Reason: $reason.")
            return null
        }

        val lazy = referenceClass(ViewModelNames.Kotlin.lazy) ?: return logDisabledReason("could not resolve Lazy<T> class")
        val observableObject = referenceClass(ViewModelNames.API.baseObservableObject) ?: return logDisabledReason("could not resolve BaseObservableObject class")
        val observe = observableObject.functions.singleOrNull {
            it.owner.name == Name.identifier("observe") && it.owner.valueParameters.singleOrNull()?.type?.classOrNull == symbols.kproperty0()
        } ?: return logDisabledReason("could not resolve `observe` method for `ObservableObject` class")
        val mutableObserve = observableObject.functions.singleOrNull {
            it.owner.name == Name.identifier("observe") && it.owner.valueParameters.singleOrNull()?.type?.classOrNull == symbols.kmutableproperty0()
        } ?: return logDisabledReason("could not resolve mutable `observe` method for `ObservableObject` class")

        val types = ViewModelIrGenerator.Types(
            lazy = lazy,
            lazyValue = lazy.getPropertyGetter(ViewModelNames.Kotlin.Lazy.value.identifier) ?: return logDisabledReason("could not resolve `value` getter for `Lazy<T>` class"),
            observe = observe,
            mutableObserve = mutableObserve,
        )
        return ViewModelIrGenerator(this, types)
    }
}
