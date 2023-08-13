package org.brightify.hyperdrive.viewmodel

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.codegen.kotlinType
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertyGetterDescriptorImpl
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.stubs.impl.Utils
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DelegatedPropertyResolver
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.annotations.argumentValue
import org.jetbrains.kotlin.resolve.calls.CallResolver
import org.jetbrains.kotlin.resolve.calls.inference.returnTypeOrNothing
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.descriptorUtil.isSubclassOf
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.resolve.lazy.LazyClassContext
import org.jetbrains.kotlin.resolve.lazy.declarations.PackageMemberDeclarationProvider
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.types.DeferredType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.KotlinTypeFactory
import org.jetbrains.kotlin.types.TypeAttributes
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.expressions.FakeCallResolver
import org.jetbrains.kotlin.types.typeUtil.createProjection
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf
import org.jetbrains.kotlin.types.typeUtil.replaceArgumentsWithStarProjections
import org.jetbrains.kotlin.types.typeUtil.supertypes

open class ViewModelResolveExtension(private val messageCollector: MessageCollector = MessageCollector.NONE): SyntheticResolveExtension {
    private companion object {
        val observableDelegates = arrayOf(
            "published",
            "collected",
            "collectedFlatMap",
            "collectedFlatMapLatest",
            "binding",
            "managed",
            "managedList",
        )
    }

    override fun getSyntheticNestedClassNames(thisDescriptor: ClassDescriptor): List<Name> = emptyList()

    override fun getPossibleSyntheticNestedClassNames(thisDescriptor: ClassDescriptor): List<Name>? = emptyList()

    override fun getSyntheticFunctionNames(thisDescriptor: ClassDescriptor): List<Name> = emptyList()

    // TODO: Test this properly to verify it works before enabling it.
    // override fun addSyntheticSupertypes(thisDescriptor: ClassDescriptor, supertypes: MutableList<KotlinType>) {
    //     super.addSyntheticSupertypes(thisDescriptor, supertypes)
    //
    //    if (thisDescriptor.annotations.hasAnnotation(ViewModelNames.Annotation.viewModel)) {
    //        val baseViewModelType = thisDescriptor.module.findClassAcrossModuleDependencies(ViewModelNames.API.baseViewModel)!!.defaultType
    //        if (supertypes.none { it == baseViewModelType || it.supertypes().contains(baseViewModelType) }) {
    //            supertypes.add(baseViewModelType)
    //        }
    //    }
    // }

    override fun generateSyntheticProperties(
        thisDescriptor: ClassDescriptor,
        name: Name,
        bindingContext: BindingContext,
        fromSupertypes: ArrayList<PropertyDescriptor>,
        result: MutableSet<PropertyDescriptor>
    ) {
        super.generateSyntheticProperties(thisDescriptor, name, bindingContext, fromSupertypes, result)

        val viewModelAnnotation = thisDescriptor.annotations.findAnnotation(ViewModelNames.Annotation.viewModel.asSingleFqName()) ?: return
        if (result.isNotEmpty() || fromSupertypes.isNotEmpty()) {
            return
        }
        val observableDelegates = viewModelAnnotation.observableDelegates

        val observableProperty = thisDescriptor.module.findClassAcrossModuleDependencies(ViewModelNames.API.observableProperty) ?: return
        val mutableObservableProperty = thisDescriptor.module.findClassAcrossModuleDependencies(ViewModelNames.API.mutableViewModelProperty) ?: return

        val referencedPropertyIdentifier = NamingHelper.getReferencedPropertyName(name.identifier) ?: return

        val refName = Name.identifier(referencedPropertyIdentifier)
        val realDescriptor = thisDescriptor.unsubstitutedMemberScope
            .getContributedVariables(refName, NoLookupLocation.FROM_SYNTHETIC_SCOPE)
            .singleOrNull() ?: return

        // This is needed because it triggers the type resolve.
        @Suppress("UNUSED_VARIABLE")
        val resolvedRealType = (realDescriptor.type as? DeferredType)?.delegate
        val delegatedType = (realDescriptor.findPsi() as? KtProperty)?.delegateExpressionOrInitializer?.kotlinType(bindingContext)
        val delegateCallExpression = (realDescriptor.findPsi() as? KtProperty)?.delegateExpression
        if (
            (delegatedType != null && delegatedType.isSubtypeOf(observableProperty.defaultType.replaceArgumentsWithStarProjections())) ||
            (delegateCallExpression != null  && observableDelegates.any { delegateCallExpression.text.startsWith("$it(") })
        ) {
            result.add(
                PropertyDescriptorImpl.create(
                    thisDescriptor,
                    Annotations.EMPTY,
                    Modality.FINAL,
                    realDescriptor.visibility,
                    false,
                    name,
                    CallableMemberDescriptor.Kind.SYNTHESIZED,
                    realDescriptor.source,
                    false,
                    false,
                    false,
                    false,
                    false,
                    true,
                ).apply {
                    val type = KotlinTypeFactory.simpleNotNullType(
                        TypeAttributes.Empty,
                        if (realDescriptor.isVar) mutableObservableProperty else observableProperty,
                        listOf(createProjection(realDescriptor.returnTypeOrNothing, Variance.INVARIANT, null))
                    )

                    initialize(
                        PropertyGetterDescriptorImpl(
                            this,
                            Annotations.EMPTY,
                            Modality.FINAL,
                            this.visibility,
                            false,
                            false,
                            false,
                            CallableMemberDescriptor.Kind.SYNTHESIZED,
                            null,
                            source
                        ).apply {
                            initialize(type)
                        },
                        null
                    )
                    setType(type, emptyList(), thisDescriptor.thisAsReceiverParameter, null, emptyList())
                }
            )
        }
    }

    override fun getSyntheticPropertiesNames(thisDescriptor: ClassDescriptor): List<Name> {
        if (!thisDescriptor.annotations.hasAnnotation(ViewModelNames.Annotation.viewModel.asSingleFqName())) {
            return emptyList()
        }

        return thisDescriptor.unsubstitutedMemberScope.getClassifierNames()?.filter {
            val realDescriptor =
                thisDescriptor.unsubstitutedMemberScope.getContributedVariables(it, NoLookupLocation.FROM_SYNTHETIC_SCOPE)
                    .singleOrNull() ?: return@filter false
            (realDescriptor.findPsi() as? KtProperty)?.hasDelegateExpression() ?: false
        }?.map {
            Name.identifier(NamingHelper.getObservingPropertyName(it.identifier))
        } ?: emptyList()
    }

    override fun getSyntheticCompanionObjectNameIfNeeded(thisDescriptor: ClassDescriptor): Name? {
        return null
    }

    private val AnnotationDescriptor.observableDelegates: Array<String>
        get() = (argumentValue("observableDelegates")?.value as? Array<String>) ?: run {
            messageCollector.report(CompilerMessageSeverity.WARNING, "Could not get argument value `@ViewModel.observableDelegates`. This is a bug in Hyperdrive, please report it.")
            Companion.observableDelegates
        }
}
