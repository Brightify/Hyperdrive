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
    private val viewModelEnabled: Boolean = false,
    private val composableAutoObserveEnabled: Boolean = false,
): IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val generator = pluginContext.viewModelIrGenerator()
        val composeViewIrGenerator = pluginContext.composeViewIrGenerator()

        for (file in moduleFragment.files) {
            generator?.runOnFilePostfix(file)
            composeViewIrGenerator?.runOnFilePostfix(file)
        }
    }

    private fun IrPluginContext.viewModelIrGenerator(): ViewModelIrGenerator? {
        fun logDisabledReason(reason: String): ViewModelIrGenerator? {
            messageCollector.report(CompilerMessageSeverity.WARNING, "ViewModel Observe Property generator disabled. Reason: $reason.")
            return null
        }

        if (!viewModelEnabled) { return null }
        val lazy = referenceClass(ViewModelNames.Kotlin.lazy) ?: return logDisabledReason("could not resolve Lazy<T> class")
        val observableObject = referenceClass(ViewModelNames.API.baseObservableObject) ?: return logDisabledReason("could not resolve BaseObservableObject class")
        val observe = observableObject.functions.singleOrNull {
            it.owner.name == Name.identifier("observe") && it.owner.valueParameters.singleOrNull()?.type?.classOrNull == symbols.kproperty0()
        } ?: return logDisabledReason("could not resolve `observe` method for `ObservableObject` class")

        val types = ViewModelIrGenerator.Types(
            lazy = lazy,
            lazyValue = lazy.getPropertyGetter(ViewModelNames.Kotlin.Lazy.value.identifier) ?: return logDisabledReason("could not resolve `value` getter for `Lazy<T>` class"),
            observe = observe,
        )
        return ViewModelIrGenerator(this, types)
    }

    private fun IrPluginContext.composeViewIrGenerator(): ComposeViewIrGenerator? {
        fun logDisabledReason(reason: String): ComposeViewIrGenerator? {
            messageCollector.report(CompilerMessageSeverity.WARNING, "@Composable VM AutoObserve disabled. Reason: $reason.")
            return null
        }

        if (!composableAutoObserveEnabled) { return null }
        val stateType = referenceClass(ViewModelNames.Compose.state) ?: return logDisabledReason("could not resolve `State<T>` class")
        val manageableViewModel = referenceClass(ViewModelNames.API.manageableViewModel.asSingleFqName()) ?: return logDisabledReason("could not resolve `ManageableViewModel` class")
        val types = ComposeViewIrGenerator.Types(
            state = stateType,
            stateValue = stateType.getPropertyGetter(ViewModelNames.Compose.stateValue) ?: return logDisabledReason("could not resolve `value` getter for `State<T>` class"),
            manageableViewModel = manageableViewModel,
            observeAsState = referenceFunctions(ViewModelNames.Compose.observeAsState).singleOrNull {
                it.owner.extensionReceiverParameter?.type?.isSubtypeOfClass(manageableViewModel) ?: false
            } ?: return logDisabledReason("could not resolve a single `ManageableViewModel.observeAsState()` function")
        )
        messageCollector.report(CompilerMessageSeverity.INFO, "@Composable VM AutoObserve enabled.")
        return ComposeViewIrGenerator(this, messageCollector, types)
    }
}
