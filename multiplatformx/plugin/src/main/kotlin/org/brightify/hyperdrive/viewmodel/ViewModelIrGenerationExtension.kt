package org.brightify.hyperdrive.viewmodel

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.runOnFilePostfix
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.util.getPropertyGetter

open class ViewModelIrGenerationExtension(
    private val messageCollector: MessageCollector = MessageCollector.NONE,
    private val autoObserveEnabled: Boolean = false,
): IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val generator = ViewModelIrGenerator(pluginContext)
        val composeViewIrGenerator = pluginContext.composeViewIrGenerator()

        for (file in moduleFragment.files) {
            generator.runOnFilePostfix(file)
            composeViewIrGenerator?.runOnFilePostfix(file)
        }
    }

    private fun IrPluginContext.composeViewIrGenerator(): ComposeViewIrGenerator? {
        fun logDisabledReason(reason: String): ComposeViewIrGenerator? {
            messageCollector.report(CompilerMessageSeverity.WARNING, "@Composable VM AutoObserve disabled. Reason: $reason.")
            return null
        }

        if (!autoObserveEnabled) { return null }
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

